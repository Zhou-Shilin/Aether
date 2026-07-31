#import "AetherISHRuntime.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <resolv.h>
#include <signal.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <util.h>
#include <TargetConditionals.h>
#include <unistd.h>

#define ISH_INTERNAL 1
#include "kernel/init.h"
#include "kernel/calls.h"
#include "kernel/task.h"
#include "kernel/fs.h"
#include "kernel/errno.h"
#include "kernel/signal.h"
#include "fs/devices.h"
#include "fs/fake.h"
#include "fs/fd.h"
#include "fs/path.h"
#include "fs/real.h"
#include "tools/fakefs.h"

static NSString *const AetherISHErrorDomain = @"com.baimoqilin.aether.ish";

@interface AetherISHProcess : NSObject
@property(nonatomic) int processId;
@property(nonatomic) int stdinWrite;
@property(nonatomic) int stdoutRead;
@property(nonatomic) int stderrRead;
@property(nonatomic) BOOL pseudoTerminal;
@property(nonatomic, copy) AetherISHOutputBlock stdoutBlock;
@property(nonatomic, copy) AetherISHOutputBlock stderrBlock;
@property(nonatomic, copy) AetherISHExitBlock exitBlock;
@property(atomic) BOOL completed;
@property(atomic) BOOL exitDelivered;
@property(nonatomic) dispatch_group_t readerGroup;
@end

@implementation AetherISHProcess
- (instancetype)init {
    self = [super init];
    if (self) {
        _stdinWrite = -1;
        _stdoutRead = -1;
        _stderrRead = -1;
    }
    return self;
}
- (void)dealloc {
    if (_stdinWrite >= 0) close(_stdinWrite);
    if (_stdoutRead >= 0) close(_stdoutRead);
    if (_stderrRead >= 0) close(_stderrRead);
}
@end

@interface AetherISHRuntime ()
@property(nonatomic) dispatch_queue_t queue;
@property(nonatomic) dispatch_queue_t outputQueue;
@property(nonatomic) NSMutableDictionary<NSNumber *, AetherISHProcess *> *processes;
@property(nonatomic, readwrite, getter=isInitialized) BOOL initialized;
@property(nonatomic) struct task *initTask;
- (void)processExited:(int)processId code:(int)code signal:(int)signal;
- (void)performGuestOperation:(dispatch_block_t)operation;
- (void)closePipe:(int[2])pipeDescriptors;
- (int)changeGuestDirectory:(const char *)path;
- (void)assignHostFd:(int)hostFd toGuestFd:(int)guestFd task:(struct task *)task;
- (void)installHostFd:(int)hostFd expectedGuestFd:(int)guestFd task:(struct task *)task;
- (int)removeSingleGuestPath:(const char *)path;
- (int)removeGuestPathRecursively:(const char *)path;
@end

static __weak AetherISHRuntime *activeRuntime;
static void *AetherISHQueueKey = &AetherISHQueueKey;

static NSError *AetherISHError(NSInteger code, NSString *message) {
    return [NSError errorWithDomain:AetherISHErrorDomain
                               code:code
                           userInfo:@{NSLocalizedDescriptionKey: message}];
}

static void AetherISHProcessExited(struct task *task, int code) {
    struct task *leader = task->group != NULL ? task->group->leader : task;
    if (leader->parent != NULL && leader->parent->parent != NULL) return;
    int processId = leader->pid;
    int exitCode = WIFEXITED(code) ? WEXITSTATUS(code) : 1;
    int exitSignal = WIFSIGNALED(code) ? WTERMSIG(code) : 0;
    dispatch_async(dispatch_get_main_queue(), ^{
        [activeRuntime processExited:processId code:exitCode signal:exitSignal];
    });
}

static void AetherISHDie(const char *message) {
    NSLog(@"Aether iSH fatal error: %s", message ?: "unknown");
}

@implementation AetherISHRuntime

+ (instancetype)sharedRuntime {
    static AetherISHRuntime *runtime;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        runtime = [[self alloc] initPrivate];
    });
    return runtime;
}

- (instancetype)initPrivate {
    self = [super init];
    if (self) {
        _queue = dispatch_queue_create("com.baimoqilin.aether.ish", DISPATCH_QUEUE_SERIAL);
        dispatch_queue_set_specific(_queue, AetherISHQueueKey, AetherISHQueueKey, NULL);
        _outputQueue = dispatch_queue_create("com.baimoqilin.aether.ish.output", DISPATCH_QUEUE_CONCURRENT);
        _processes = [NSMutableDictionary dictionary];
        activeRuntime = self;
    }
    return self;
}

- (instancetype)init {
    return [AetherISHRuntime sharedRuntime];
}

- (void)initializeWithProgress:(AetherISHProgressBlock)progress
                    completion:(AetherISHCompletionBlock)completion {
    dispatch_async(self.queue, ^{
        if (self.initialized) {
            dispatch_async(dispatch_get_main_queue(), ^{ completion(nil); });
            return;
        }
        progress(@"rootfs", @"Preparing Alpine root filesystem", 0.05);
        NSError *error = nil;
        NSURL *rootURL = [self prepareRootFileSystemWithProgress:progress error:&error];
        if (!rootURL) {
            dispatch_async(dispatch_get_main_queue(), ^{ completion(error); });
            return;
        }
        progress(@"kernel", @"Starting Alpine", 0.72);
        int bootError = [self bootRootAtURL:rootURL];
        if (bootError < 0) {
            error = AetherISHError(bootError, [NSString stringWithFormat:@"Alpine boot failed (%d).", bootError]);
            dispatch_async(dispatch_get_main_queue(), ^{ completion(error); });
            return;
        }
        self.initialized = YES;
        progress(@"kernel", @"Alpine started", 0.78);
        dispatch_async(dispatch_get_main_queue(), ^{ completion(nil); });
    });
}

- (NSURL *)prepareRootFileSystemWithProgress:(AetherISHProgressBlock)progress
                                       error:(NSError **)error {
    NSURL *applicationSupport = [NSFileManager.defaultManager URLForDirectory:NSApplicationSupportDirectory
                                                                     inDomain:NSUserDomainMask
                                                            appropriateForURL:nil
                                                                       create:YES
                                                                        error:error];
    if (!applicationSupport) return nil;
    NSURL *rootURL = [applicationSupport URLByAppendingPathComponent:@"AetherAlpine" isDirectory:YES];
    NSURL *dataURL = [rootURL URLByAppendingPathComponent:@"data" isDirectory:YES];
    NSURL *databaseURL = [rootURL URLByAppendingPathComponent:@"meta.db"];
    if ([NSFileManager.defaultManager fileExistsAtPath:dataURL.path] &&
        [NSFileManager.defaultManager fileExistsAtPath:databaseURL.path]) {
        return rootURL;
    }

    NSURL *archiveURL = [NSBundle.mainBundle URLForResource:@"root" withExtension:@"tar.gz"];
    if (!archiveURL) {
        if (error) *error = AetherISHError(1, @"Bundled Alpine rootfs is missing.");
        return nil;
    }
    [NSFileManager.defaultManager removeItemAtURL:rootURL error:nil];
    NSURL *temporaryURL = [NSFileManager.defaultManager.temporaryDirectory
        URLByAppendingPathComponent:NSProcessInfo.processInfo.globallyUniqueString isDirectory:YES];
    struct fakefsify_error importError = {0};
    progress(@"rootfs", @"Extracting Alpine", 0.18);
    bool imported = fakefs_import(archiveURL.fileSystemRepresentation,
                                  temporaryURL.fileSystemRepresentation,
                                  &importError,
                                  (struct progress){0});
    if (!imported) {
        NSString *message = importError.message
            ? [NSString stringWithUTF8String:importError.message]
            : @"Unable to import Alpine rootfs.";
        if (error) *error = AetherISHError(importError.code, message);
        free(importError.message);
        [NSFileManager.defaultManager removeItemAtURL:temporaryURL error:nil];
        return nil;
    }
    if (![NSFileManager.defaultManager moveItemAtURL:temporaryURL toURL:rootURL error:error]) return nil;
    return rootURL;
}

- (int)bootRootAtURL:(NSURL *)rootURL {
    NSURL *dataURL = [rootURL URLByAppendingPathComponent:@"data" isDirectory:YES];
    fakefs_set_rootfs_data_path(dataURL.fileSystemRepresentation);
    int error = mount_root(&fakefs, dataURL.fileSystemRepresentation);
    if (error < 0) return error;
    error = become_first_process();
    if (error < 0) return error;
    self.initTask = current;

    generic_mkdirat(AT_PWD, "/dev", 0755);
    generic_mkdirat(AT_PWD, "/dev/pts", 0755);
    generic_mknodat(AT_PWD, "/dev/null", S_IFCHR | 0666, dev_make(MEM_MAJOR, DEV_NULL_MINOR));
    generic_mknodat(AT_PWD, "/dev/zero", S_IFCHR | 0666, dev_make(MEM_MAJOR, DEV_ZERO_MINOR));
    generic_mknodat(AT_PWD, "/dev/random", S_IFCHR | 0666, dev_make(MEM_MAJOR, DEV_RANDOM_MINOR));
    generic_mknodat(AT_PWD, "/dev/urandom", S_IFCHR | 0666, dev_make(MEM_MAJOR, DEV_URANDOM_MINOR));
    do_mount(&procfs, "proc", "/proc", "", 0);
    do_mount(&devptsfs, "devpts", "/dev/pts", "", 0);

    exit_hook = AetherISHProcessExited;
    die_handler = AetherISHDie;
#if !TARGET_OS_SIMULATOR
    NSString *socketPrefix = [NSTemporaryDirectory() stringByAppendingString:@"aether-ish-sock"];
    sock_tmp_prefix = strdup(socketPrefix.UTF8String);
#endif
    [self configureDNS];

    const char *argv = "/bin/sh\0-c\0while :; do sleep 86400; done\0";
    const char *envp = "TERM=xterm-256color\0HOME=/root\0PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\0PYTHONMALLOC=malloc\0";
    error = do_execve("/bin/sh", 3, argv, envp);
    if (error < 0) return error;
    task_start(current);
    return 0;
}

- (void)configureDNS {
    struct __res_state resolver;
    if (res_ninit(&resolver) != EXIT_SUCCESS) return;
    NSMutableString *contents = [NSMutableString string];
    union res_sockaddr_union servers[NI_MAXSERV];
    int count = res_getservers(&resolver, servers, NI_MAXSERV);
    for (int index = 0; index < count; index++) {
        union res_sockaddr_union server = servers[index];
        if (server.sin.sin_len == 0) continue;
        char address[NI_MAXHOST];
        getnameinfo((struct sockaddr *)&server.sin, server.sin.sin_len,
                    address, sizeof(address), NULL, 0, NI_NUMERICHOST);
        [contents appendFormat:@"nameserver %s\n", address];
    }
    if (contents.length == 0) [contents appendString:@"nameserver 1.1.1.1\n"];
    struct fd *fd = generic_open("/etc/resolv.conf", O_WRONLY_ | O_CREAT_ | O_TRUNC_, 0644);
    if (!IS_ERR(fd)) {
        NSData *data = [contents dataUsingEncoding:NSUTF8StringEncoding];
        fd->ops->write(fd, data.bytes, data.length);
        fd_close(fd);
    }
    res_nclose(&resolver);
}

- (int)startExecutable:(NSString *)executable
             arguments:(NSArray<NSString *> *)arguments
           environment:(NSDictionary<NSString *,NSString *> *)environment
      workingDirectory:(NSString *)workingDirectory
        pseudoTerminal:(BOOL)pseudoTerminal
    remoteDebuggingPipe:(BOOL)remoteDebuggingPipe
        standardOutput:(AetherISHOutputBlock)stdoutBlock
         standardError:(AetherISHOutputBlock)stderrBlock
                   exit:(AetherISHExitBlock)exitBlock {
    if (!self.initialized) return -1;
    __block int processId = -1;
    [self performGuestOperation:^{
        int stdinPipe[2] = {-1, -1};
        int stdoutPipe[2] = {-1, -1};
        int stderrPipe[2] = {-1, -1};
        int terminalMaster = -1;
        int terminalSlave = -1;
        if (pseudoTerminal) {
            struct winsize initialSize = {.ws_row = 24, .ws_col = 80};
            if (openpty(&terminalMaster, &terminalSlave, NULL, NULL, &initialSize) != 0) {
                return;
            }
        } else if (pipe(stdinPipe) || pipe(stdoutPipe) || pipe(stderrPipe)) {
            [self closePipe:stdinPipe];
            [self closePipe:stdoutPipe];
            [self closePipe:stderrPipe];
            return;
        }

        struct task *savedCurrent = current;
        int error = become_new_init_child();
        if (error < 0) {
            current = savedCurrent;
            [self closePipe:stdinPipe];
            [self closePipe:stdoutPipe];
            [self closePipe:stderrPipe];
            return;
        }
        struct task *task = current;
        if (pseudoTerminal) {
            [self assignHostFd:dup(terminalSlave) toGuestFd:0 task:task];
            [self assignHostFd:dup(terminalSlave) toGuestFd:1 task:task];
            [self assignHostFd:dup(terminalSlave) toGuestFd:2 task:task];
            close(terminalSlave);
            terminalSlave = -1;
        } else if (remoteDebuggingPipe) {
            [self assignHostFd:open("/dev/null", O_RDONLY) toGuestFd:0 task:task];
            [self assignHostFd:open("/dev/null", O_WRONLY) toGuestFd:1 task:task];
            [self assignHostFd:dup(stderrPipe[1]) toGuestFd:2 task:task];
            [self installHostFd:dup(stdinPipe[0]) expectedGuestFd:3 task:task];
            [self installHostFd:dup(stdoutPipe[1]) expectedGuestFd:4 task:task];
            close(stdinPipe[0]);
            stdinPipe[0] = -1;
            close(stdoutPipe[1]);
            stdoutPipe[1] = -1;
            close(stderrPipe[1]);
            stderrPipe[1] = -1;
        } else {
            [self assignHostFd:dup(stdinPipe[0]) toGuestFd:0 task:task];
            [self assignHostFd:dup(stdoutPipe[1]) toGuestFd:1 task:task];
            [self assignHostFd:dup(stderrPipe[1]) toGuestFd:2 task:task];
            close(stdinPipe[0]);
            stdinPipe[0] = -1;
            close(stdoutPipe[1]);
            stdoutPipe[1] = -1;
            close(stderrPipe[1]);
            stderrPipe[1] = -1;
        }

        NSMutableArray<NSString *> *allArguments = [NSMutableArray arrayWithObject:executable];
        [allArguments addObjectsFromArray:arguments ?: @[]];
        char argumentBuffer[16384] = {0};
        size_t argumentPosition = 0;
        for (NSString *argument in allArguments) {
            NSData *data = [argument dataUsingEncoding:NSUTF8StringEncoding];
            if (argumentPosition + data.length + 2 >= sizeof(argumentBuffer)) {
                current = savedCurrent;
                [self closePipe:stdinPipe];
                [self closePipe:stdoutPipe];
                [self closePipe:stderrPipe];
                return;
            }
            memcpy(argumentBuffer + argumentPosition, data.bytes, data.length);
            argumentPosition += data.length + 1;
        }

        NSMutableData *environmentData = [NSMutableData data];
        NSDictionary *defaults = @{
            @"TERM": @"xterm-256color",
            @"HOME": @"/root",
            @"PATH": @"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            @"PYTHONMALLOC": @"malloc",
        };
        NSMutableDictionary *mergedEnvironment = [defaults mutableCopy];
        [mergedEnvironment addEntriesFromDictionary:environment ?: @{}];
        [mergedEnvironment enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString *value, BOOL *stop) {
            NSData *entry = [[NSString stringWithFormat:@"%@=%@", key, value] dataUsingEncoding:NSUTF8StringEncoding];
            [environmentData appendData:entry];
            uint8_t zero = 0;
            [environmentData appendBytes:&zero length:1];
        }];
        uint8_t zero = 0;
        [environmentData appendBytes:&zero length:1];

        if (workingDirectory.length > 0) {
            error = [self changeGuestDirectory:workingDirectory.UTF8String];
            if (error < 0) {
                current = savedCurrent;
                [self closePipe:stdinPipe];
                [self closePipe:stdoutPipe];
                [self closePipe:stderrPipe];
                return;
            }
        }
        error = do_execve(executable.UTF8String, allArguments.count, argumentBuffer, environmentData.bytes);
        if (error < 0) {
            current = savedCurrent;
            [self closePipe:stdinPipe];
            [self closePipe:stdoutPipe];
            [self closePipe:stderrPipe];
            return;
        }

        AetherISHProcess *process = [AetherISHProcess new];
        process.processId = task->pid;
        process.pseudoTerminal = pseudoTerminal;
        process.stdinWrite = pseudoTerminal ? terminalMaster : stdinPipe[1];
        process.stdoutRead = pseudoTerminal ? dup(terminalMaster) : stdoutPipe[0];
        process.stderrRead = pseudoTerminal ? -1 : stderrPipe[0];
        process.stdoutBlock = stdoutBlock;
        process.stderrBlock = stderrBlock;
        process.exitBlock = exitBlock;
        process.readerGroup = dispatch_group_create();
        fcntl(process.stdoutRead, F_SETFL, O_NONBLOCK);
        fcntl(process.stderrRead, F_SETFL, O_NONBLOCK);
        @synchronized(self.processes) {
            self.processes[@(process.processId)] = process;
        }
        processId = process.processId;
        task_start(task);
        current = savedCurrent;
        [self readDescriptor:process.stdoutRead process:process block:stdoutBlock];
        if (process.stderrRead >= 0) {
            [self readDescriptor:process.stderrRead process:process block:stderrBlock];
        }
    }];
    return processId;
}

- (void)performGuestOperation:(dispatch_block_t)operation {
    dispatch_block_t guarded = ^{
        struct task *savedCurrent = current;
        current = self.initTask;
        operation();
        current = savedCurrent;
    };
    if (dispatch_get_specific(AetherISHQueueKey)) {
        guarded();
    } else {
        dispatch_sync(self.queue, guarded);
    }
}

- (void)closePipe:(int[2])pipeDescriptors {
    if (pipeDescriptors[0] >= 0) close(pipeDescriptors[0]);
    if (pipeDescriptors[1] >= 0) close(pipeDescriptors[1]);
    pipeDescriptors[0] = pipeDescriptors[1] = -1;
}

- (int)changeGuestDirectory:(const char *)path {
    struct statbuf stat = {0};
    int result = generic_statat(AT_PWD, path, &stat, true);
    if (result < 0) return result;
    if (!S_ISDIR(stat.mode)) return _ENOTDIR;
    struct fd *directory = generic_open(path, O_RDONLY_, 0);
    if (IS_ERR(directory)) return PTR_ERR(directory);
    fs_chdir(current->fs, directory);
    return 0;
}

- (void)assignHostFd:(int)hostFd toGuestFd:(int)guestFd task:(struct task *)task {
    struct fd *fd = adhoc_fd_create(&realfs_fdops);
    fd->real_fd = hostFd;
    task->files->files[guestFd] = fd;
}

- (void)installHostFd:(int)hostFd expectedGuestFd:(int)guestFd task:(struct task *)task {
    struct fd *fd = adhoc_fd_create(&realfs_fdops);
    fd->real_fd = hostFd;
    int installed = f_install(fd, 0);
    NSAssert(installed == guestFd, @"Expected guest fd %d, got %d", guestFd, installed);
}

- (void)readDescriptor:(int)descriptor process:(AetherISHProcess *)process block:(AetherISHOutputBlock)block {
    dispatch_group_enter(process.readerGroup);
    dispatch_async(self.outputQueue, ^{
        uint8_t buffer[8192];
        int idleReadsAfterExit = 0;
        while (true) {
            ssize_t count = read(descriptor, buffer, sizeof(buffer));
            if (count > 0) {
                idleReadsAfterExit = 0;
                NSData *data = [NSData dataWithBytes:buffer length:(NSUInteger)count];
                dispatch_async(dispatch_get_main_queue(), ^{ block(data); });
            } else if (count == 0) {
                break;
            } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
                if (process.completed && ++idleReadsAfterExit >= 2) break;
                usleep(10000);
            } else {
                break;
            }
        }
        dispatch_group_leave(process.readerGroup);
    });
}

- (void)processExited:(int)processId code:(int)code signal:(int)signal {
    AetherISHProcess *process;
    @synchronized(self.processes) {
        process = self.processes[@(processId)];
        if (!process || process.completed) return;
        process.completed = YES;
        [self.processes removeObjectForKey:@(processId)];
    }
    if (process.stdinWrite >= 0) {
        close(process.stdinWrite);
        process.stdinWrite = -1;
    }
    void (^deliverExit)(void) = ^{
        @synchronized(process) {
            if (process.exitDelivered) return;
            process.exitDelivered = YES;
        }
        process.exitBlock(code, signal);
    };
    dispatch_group_notify(process.readerGroup, dispatch_get_main_queue(), deliverExit);
    dispatch_after(
        dispatch_time(DISPATCH_TIME_NOW, (int64_t)NSEC_PER_SEC),
        dispatch_get_main_queue(),
        deliverExit
    );
}

- (BOOL)writeStdin:(NSData *)bytes processId:(int)processId {
    AetherISHProcess *process;
    @synchronized(self.processes) {
        process = self.processes[@(processId)];
    }
    if (!process || process.stdinWrite < 0) return NO;
    const uint8_t *cursor = bytes.bytes;
    NSUInteger remaining = bytes.length;
    while (remaining > 0) {
        ssize_t written = write(process.stdinWrite, cursor, remaining);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) return NO;
        cursor += written;
        remaining -= (NSUInteger)written;
    }
    return YES;
}

- (void)closeStdinForProcessId:(int)processId {
    @synchronized(self.processes) {
        AetherISHProcess *process = self.processes[@(processId)];
        if (process.stdinWrite >= 0) {
            close(process.stdinWrite);
            process.stdinWrite = -1;
        }
    }
}

- (void)signalProcessId:(int)processId signal:(int)signal {
    lock(&pids_lock);
    struct task *task = pid_get_task(processId);
    if (task) send_signal(task, signal, SIGINFO_NIL);
    unlock(&pids_lock);
}

- (void)resizeTerminalForProcessId:(int)processId columns:(int)columns rows:(int)rows {
    AetherISHProcess *process;
    @synchronized(self.processes) {
        process = self.processes[@(processId)];
    }
    if (!process || !process.pseudoTerminal || process.stdinWrite < 0) return;
    struct winsize size = {
        .ws_row = (unsigned short)MAX(rows, 1),
        .ws_col = (unsigned short)MAX(columns, 1),
    };
    ioctl(process.stdinWrite, TIOCSWINSZ, &size);
}

- (BOOL)fileExists:(NSString *)path {
    __block BOOL exists = NO;
    [self performGuestOperation:^{
        struct statbuf stat = {0};
        exists = generic_statat(AT_PWD, path.UTF8String, &stat, true) == 0;
    }];
    return exists;
}

- (NSData *)readFile:(NSString *)path error:(NSError **)error {
    return [self readFile:path maximumBytes:NSUIntegerMax error:error];
}

- (NSData *)readFile:(NSString *)path maximumBytes:(NSUInteger)maximumBytes error:(NSError **)error {
    __block NSData *result = nil;
    __block NSError *operationError = nil;
    [self performGuestOperation:^{
        struct fd *fd = generic_open(path.UTF8String, O_RDONLY_, 0);
        if (IS_ERR(fd)) {
            operationError = AetherISHError(PTR_ERR(fd), @"Unable to open guest file.");
            return;
        }
        NSMutableData *contents = [NSMutableData data];
        uint8_t buffer[8192];
        while (true) {
            ssize_t count = fd->ops->read(fd, buffer, sizeof(buffer));
            if (count < 0) {
                operationError = AetherISHError(count, @"Unable to read guest file.");
                break;
            }
            if (count == 0) break;
            if ((NSUInteger)count > maximumBytes - MIN(contents.length, maximumBytes)) {
                operationError = AetherISHError(_EFBIG, @"Guest file exceeds the allowed size.");
                break;
            }
            [contents appendBytes:buffer length:(NSUInteger)count];
        }
        fd_close(fd);
        if (!operationError) result = [contents copy];
    }];
    if (error) *error = operationError;
    return result;
}

- (NSData *)readFilePrefix:(NSString *)path maximumBytes:(NSUInteger)maximumBytes error:(NSError **)error {
    __block NSData *result = nil;
    __block NSError *operationError = nil;
    [self performGuestOperation:^{
        struct fd *fd = generic_open(path.UTF8String, O_RDONLY_, 0);
        if (IS_ERR(fd)) {
            operationError = AetherISHError(PTR_ERR(fd), @"Unable to open guest file.");
            return;
        }
        NSMutableData *contents = [NSMutableData data];
        uint8_t buffer[8192];
        while (contents.length < maximumBytes) {
            size_t remaining = maximumBytes - contents.length;
            size_t requested = MIN(sizeof(buffer), remaining);
            ssize_t count = fd->ops->read(fd, buffer, requested);
            if (count < 0) {
                operationError = AetherISHError(count, @"Unable to read guest file.");
                break;
            }
            if (count == 0) break;
            [contents appendBytes:buffer length:(NSUInteger)count];
        }
        fd_close(fd);
        if (!operationError) result = [contents copy];
    }];
    if (error) *error = operationError;
    return result;
}

- (BOOL)writeFile:(NSString *)path data:(NSData *)data executable:(BOOL)executable error:(NSError **)error {
    return [self writeFile:path data:data executable:executable progress:nil error:error];
}

- (BOOL)writeFile:(NSString *)path
             data:(NSData *)data
       executable:(BOOL)executable
         progress:(AetherISHFileWriteProgressBlock)progress
            error:(NSError **)error {
    __block NSError *operationError = nil;
    [self performGuestOperation:^{
        struct fd *fd = generic_open(path.UTF8String, O_WRONLY_ | O_CREAT_ | O_TRUNC_, executable ? 0755 : 0644);
        if (IS_ERR(fd)) {
            operationError = AetherISHError(PTR_ERR(fd), @"Unable to open guest file for writing.");
            return;
        }
        const uint8_t *cursor = data.bytes;
        NSUInteger remaining = data.length;
        NSUInteger bytesCopied = 0;
        while (remaining > 0) {
            size_t requested = MIN(remaining, (NSUInteger)(64 * 1024));
            ssize_t count = fd->ops->write(fd, cursor, requested);
            if (count <= 0) {
                operationError = AetherISHError(count, @"Unable to write complete guest file.");
                break;
            }
            cursor += count;
            remaining -= (NSUInteger)count;
            bytesCopied += (NSUInteger)count;
            if (progress) progress(bytesCopied);
        }
        fd_close(fd);
    }];
    if (error) *error = operationError;
    return operationError == nil;
}

- (BOOL)createDirectories:(NSString *)path error:(NSError **)error {
    __block NSError *operationError = nil;
    [self performGuestOperation:^{
        NSArray<NSString *> *components = [path pathComponents];
        NSMutableString *currentPath = [NSMutableString string];
        for (NSString *component in components) {
            if ([component isEqualToString:@"/"]) continue;
            [currentPath appendFormat:@"/%@", component];
            int result = generic_mkdirat(AT_PWD, currentPath.UTF8String, 0755);
            if (result < 0 && result != _EEXIST) {
                operationError = AetherISHError(result, @"Unable to create guest directory.");
                break;
            }
        }
    }];
    if (error) *error = operationError;
    return operationError == nil;
}

- (BOOL)removePath:(NSString *)path recursive:(BOOL)recursive error:(NSError **)error {
    __block int result = 0;
    [self performGuestOperation:^{
        result = recursive
            ? [self removeGuestPathRecursively:path.UTF8String]
            : [self removeSingleGuestPath:path.UTF8String];
    }];
    if (result < 0 && error) *error = AetherISHError(result, @"Unable to remove guest path.");
    return result >= 0;
}

- (int)removeSingleGuestPath:(const char *)path {
    struct statbuf stat = {0};
    int result = generic_statat(AT_PWD, path, &stat, false);
    if (result < 0) return result;
    return S_ISDIR(stat.mode)
        ? generic_rmdirat(AT_PWD, path)
        : generic_unlinkat(AT_PWD, path);
}

- (int)removeGuestPathRecursively:(const char *)path {
    struct statbuf stat = {0};
    int result = generic_statat(AT_PWD, path, &stat, false);
    if (result < 0) return result;
    if (!S_ISDIR(stat.mode)) return generic_unlinkat(AT_PWD, path);

    struct fd *directory = generic_open(path, O_RDONLY_ | O_DIRECTORY_, 0);
    if (IS_ERR(directory)) return PTR_ERR(directory);
    struct dir_entry entry;
    while ((result = directory->ops->readdir(directory, &entry)) > 0) {
        if (strcmp(entry.name, ".") == 0 || strcmp(entry.name, "..") == 0) continue;
        NSString *child = [[NSString stringWithUTF8String:path]
            stringByAppendingPathComponent:[NSString stringWithUTF8String:entry.name]];
        result = [self removeGuestPathRecursively:child.UTF8String];
        if (result < 0) break;
    }
    fd_close(directory);
    if (result < 0) return result;
    return generic_rmdirat(AT_PWD, path);
}

- (BOOL)bindHostPath:(NSString *)hostPath guestPath:(NSString *)guestPath readOnly:(BOOL)readOnly error:(NSError **)error {
    if (![hostPath hasPrefix:NSHomeDirectory()]) {
        if (error) *error = AetherISHError(1, @"Bind mount must remain inside the application sandbox.");
        return NO;
    }
    __block int result = 0;
    [self performGuestOperation:^{
        result = fakefs_bind_mount(guestPath.UTF8String, hostPath.UTF8String, readOnly);
    }];
    if (result < 0 && error) *error = AetherISHError(result, @"Unable to create bind mount.");
    return result >= 0;
}

@end
