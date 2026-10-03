//
//  ANSIParser.swift
//  MinisApp
//  Adapted for Aether from OpenMinis (GPL-3.0).
//
//  VT100/xterm escape sequence parser (state machine)
//

import Foundation

// MARK: - Parsed Actions

/// Actions emitted by the parser
enum ParsedAction {
    /// A printable character to display
    case printable(Character)

    /// A C0 control character (BEL, BS, HT, LF, VT, FF, CR)
    case controlChar(UInt8)

    /// CSI sequence: ESC [ params intermediate finalByte
    /// params are semicolon-separated integers, intermediate is optional prefix char
    case csiDispatch(params: [Int], intermediate: Character?, finalByte: Character)

    /// ESC sequence: ESC followed by a single character
    case escDispatch(Character)

    /// OSC string: ESC ] Ps ; Pt ST
    case oscDispatch(command: Int, payload: String)
}

// MARK: - Parser State Machine

/// VT100/xterm escape sequence parser
///
/// Implements a simplified version of the DEC/ECMA state machine.
/// Feeds raw bytes and emits `ParsedAction` values via a callback.
final class ANSIParser {
    /// Parser states
    private enum State {
        case ground
        case escape
        case escapeIntermediate
        case csiEntry
        case csiParam
        case csiIntermediate
        case oscString
        case oscParam
    }

    private var state: State = .ground

    // CSI accumulation
    private var csiParams: [Int] = []
    private var currentParam: Int = 0
    private var hasParam: Bool = false
    private var csiIntermediate: Character?

    // OSC accumulation
    private var oscCommand: Int = 0
    private var oscPayload: String = ""
    private var oscHasCommand: Bool = false
    // UTF-8 accumulation for OSC payload (titles may contain multibyte text)
    private var oscUtf8Buffer: [UInt8] = []
    private var oscUtf8Remaining: Int = 0

    // ESC intermediate
    private var escIntermediate: Character?

    // UTF-8 decoding
    private var utf8Buffer: [UInt8] = []
    private var utf8Remaining: Int = 0

    /// Feed raw data to the parser, emitting actions via the callback
    func feed(_ data: Data, action: (ParsedAction) -> Void) {
        for byte in data {
            processByte(byte, action: action)
        }
    }

    /// Feed raw bytes to the parser
    func feed(_ bytes: [UInt8], action: (ParsedAction) -> Void) {
        for byte in bytes {
            processByte(byte, action: action)
        }
    }

    /// Reset parser state
    func reset() {
        state = .ground
        csiParams = []
        currentParam = 0
        hasParam = false
        csiIntermediate = nil
        oscCommand = 0
        oscPayload = ""
        oscHasCommand = false
        oscUtf8Buffer = []
        oscUtf8Remaining = 0
        escIntermediate = nil
        utf8Buffer = []
        utf8Remaining = 0
    }

    // MARK: - Private

    private func processByte(_ byte: UInt8, action: (ParsedAction) -> Void) {
        // Handle UTF-8 continuation bytes in ground state
        if utf8Remaining > 0 {
            if byte & 0xC0 == 0x80 {
                utf8Buffer.append(byte)
                utf8Remaining -= 1
                if utf8Remaining == 0 {
                    if let str = String(bytes: utf8Buffer, encoding: .utf8),
                       let char = str.first {
                        action(.printable(char))
                    }
                    utf8Buffer.removeAll()
                }
                return
            } else {
                // Invalid continuation — discard and reprocess
                utf8Buffer.removeAll()
                utf8Remaining = 0
            }
        }

        switch state {
        case .ground:
            processGround(byte, action: action)
        case .escape:
            processEscape(byte, action: action)
        case .escapeIntermediate:
            processEscapeIntermediate(byte, action: action)
        case .csiEntry:
            processCSIEntry(byte, action: action)
        case .csiParam:
            processCSIParam(byte, action: action)
        case .csiIntermediate:
            processCSIIntermediate(byte, action: action)
        case .oscParam:
            processOSCParam(byte, action: action)
        case .oscString:
            processOSCString(byte, action: action)
        }
    }

    // MARK: - Ground State

    private func processGround(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x1B: // ESC
            state = .escape
        case 0x00...0x1A, 0x1C...0x1F:
            // C0 control characters (excluding ESC=0x1B)
            action(.controlChar(byte))
        case 0x20...0x7E:
            // Printable ASCII
            action(.printable(Character(UnicodeScalar(byte))))
        case 0x7F:
            // DEL — ignore in output
            break
        case 0xC0...0xDF:
            // UTF-8 2-byte start
            utf8Buffer = [byte]
            utf8Remaining = 1
        case 0xE0...0xEF:
            // UTF-8 3-byte start
            utf8Buffer = [byte]
            utf8Remaining = 2
        case 0xF0...0xF7:
            // UTF-8 4-byte start
            utf8Buffer = [byte]
            utf8Remaining = 3
        default:
            // 0x80-0xBF or 0xF8+: invalid as start byte, ignore
            break
        }
    }

    // MARK: - Escape State

    private func processEscape(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x5B: // [  → CSI
            state = .csiEntry
            csiParams = []
            currentParam = 0
            hasParam = false
            csiIntermediate = nil
        case 0x5D: // ]  → OSC
            state = .oscParam
            oscCommand = 0
            oscPayload = ""
            oscHasCommand = false
        case 0x20...0x2F: // SP to /  → intermediate
            escIntermediate = Character(UnicodeScalar(byte))
            state = .escapeIntermediate
        case 0x30...0x7E: // 0-~ → dispatch
            action(.escDispatch(Character(UnicodeScalar(byte))))
            state = .ground
        case 0x1B: // Another ESC
            // Stay in escape state (new ESC replaces old)
            break
        default:
            // Invalid — back to ground
            state = .ground
        }
    }

    // MARK: - Escape Intermediate

    private func processEscapeIntermediate(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x20...0x2F:
            // More intermediate bytes (rare, just update)
            escIntermediate = Character(UnicodeScalar(byte))
        case 0x30...0x7E:
            // Final byte — dispatch as ESC with intermediate
            // For now we combine intermediate + final into a dispatch
            // e.g., ESC ( B = G0 charset, ESC ) 0 = G1 charset
            // We dispatch the final byte and let the emulator handle the combined sequence
            action(.escDispatch(Character(UnicodeScalar(byte))))
            state = .ground
        case 0x1B:
            state = .escape
        default:
            state = .ground
        }
    }

    // MARK: - CSI States

    private func processCSIEntry(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x30...0x39: // 0-9
            currentParam = Int(byte - 0x30)
            hasParam = true
            state = .csiParam
        case 0x3B: // ;
            csiParams.append(0)
            state = .csiParam
        case 0x3C...0x3F: // < = > ? — private mode indicator
            csiIntermediate = Character(UnicodeScalar(byte))
            state = .csiParam
        case 0x20...0x2F: // intermediate
            csiIntermediate = Character(UnicodeScalar(byte))
            state = .csiIntermediate
        case 0x40...0x7E: // @ to ~ — final byte with no params
            action(.csiDispatch(params: [], intermediate: nil, finalByte: Character(UnicodeScalar(byte))))
            state = .ground
        case 0x1B:
            state = .escape
        default:
            state = .ground
        }
    }

    private func processCSIParam(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x30...0x39: // 0-9
            currentParam = currentParam * 10 + Int(byte - 0x30)
            hasParam = true
        case 0x3B: // ;
            csiParams.append(hasParam ? currentParam : 0)
            currentParam = 0
            hasParam = false
        case 0x3C...0x3F: // < = > ? — should have been at entry, but handle gracefully
            if csiIntermediate == nil {
                csiIntermediate = Character(UnicodeScalar(byte))
            }
        case 0x20...0x2F: // intermediate bytes
            if hasParam {
                csiParams.append(currentParam)
                currentParam = 0
                hasParam = false
            }
            csiIntermediate = csiIntermediate ?? Character(UnicodeScalar(byte))
            state = .csiIntermediate
        case 0x40...0x7E: // final byte
            if hasParam {
                csiParams.append(currentParam)
            }
            action(.csiDispatch(
                params: csiParams,
                intermediate: csiIntermediate,
                finalByte: Character(UnicodeScalar(byte))
            ))
            state = .ground
        case 0x1B:
            state = .escape
        default:
            state = .ground
        }
    }

    private func processCSIIntermediate(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x20...0x2F:
            // More intermediate bytes
            break
        case 0x40...0x7E: // final byte
            if hasParam {
                csiParams.append(currentParam)
            }
            action(.csiDispatch(
                params: csiParams,
                intermediate: csiIntermediate,
                finalByte: Character(UnicodeScalar(byte))
            ))
            state = .ground
        case 0x1B:
            state = .escape
        default:
            state = .ground
        }
    }

    // MARK: - OSC States

    private func processOSCParam(_ byte: UInt8, action: (ParsedAction) -> Void) {
        switch byte {
        case 0x30...0x39: // 0-9
            oscCommand = oscCommand * 10 + Int(byte - 0x30)
            oscHasCommand = true
        case 0x3B: // ;
            state = .oscString
        case 0x07: // BEL — string terminator
            action(.oscDispatch(command: oscCommand, payload: oscPayload))
            state = .ground
        case 0x1B: // ESC — possible ST (ESC \)
            // We handle this by peeking; for simplicity, just dispatch
            action(.oscDispatch(command: oscCommand, payload: oscPayload))
            state = .escape
        default:
            state = .ground
        }
    }

    private func flushOscUtf8() {
        if !oscUtf8Buffer.isEmpty,
           let str = String(bytes: oscUtf8Buffer, encoding: .utf8) {
            oscPayload.append(contentsOf: str)
        }
        oscUtf8Buffer.removeAll()
        oscUtf8Remaining = 0
    }

    private func processOSCString(_ byte: UInt8, action: (ParsedAction) -> Void) {
        // Handle in-progress UTF-8 sequence first
        if oscUtf8Remaining > 0 {
            if byte & 0xC0 == 0x80 {
                oscUtf8Buffer.append(byte)
                oscUtf8Remaining -= 1
                if oscUtf8Remaining == 0 {
                    flushOscUtf8()
                }
                return
            } else {
                // Invalid continuation — discard pending bytes and reprocess this one
                oscUtf8Buffer.removeAll()
                oscUtf8Remaining = 0
            }
        }

        switch byte {
        case 0x07: // BEL — string terminator
            flushOscUtf8()
            action(.oscDispatch(command: oscCommand, payload: oscPayload))
            state = .ground
        case 0x1B: // ESC — possible ST (ESC \)
            flushOscUtf8()
            action(.oscDispatch(command: oscCommand, payload: oscPayload))
            state = .escape
        case 0x20...0x7E:
            // Printable ASCII
            oscPayload.append(Character(UnicodeScalar(byte)))
        case 0xC0...0xDF:
            // UTF-8 2-byte start
            oscUtf8Buffer = [byte]
            oscUtf8Remaining = 1
        case 0xE0...0xEF:
            // UTF-8 3-byte start
            oscUtf8Buffer = [byte]
            oscUtf8Remaining = 2
        case 0xF0...0xF7:
            // UTF-8 4-byte start
            oscUtf8Buffer = [byte]
            oscUtf8Remaining = 3
        default:
            // Control chars or invalid bytes in OSC — ignore
            break
        }
    }
}
