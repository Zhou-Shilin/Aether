package com.zhousl.aether.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhousl.aether.R
import com.zhousl.aether.data.AlpineChromeViewerUrl
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val ImeBridgeSentinelLength = 32
private val ImeBridgeSentinel = "\u200B".repeat(ImeBridgeSentinelLength)

@Composable
fun AlpineChromeScreen(
    onStart: suspend () -> Result<Unit>,
    onShouldShowKeyboard: suspend (Int, Int) -> Result<Boolean>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFocusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var viewerReady by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var imeBridgeValue by remember { mutableStateOf(newImeBridgeValue()) }

    fun startOrRefresh() {
        scope.launch {
            isStarting = true
            onStart()
                .onSuccess {
                    errorMessage = ""
                    viewerReady = true
                    webView?.loadUrl(AlpineChromeViewerUrl)
                }
                .onFailure {
                    viewerReady = false
                    errorMessage = it.message ?: "Unable to start Chrome."
                }
            isStarting = false
        }
    }

    fun sendKey(key: String) {
        webView?.sendVncKey(key)
    }

    fun submitInput() {
        sendKey("Enter")
    }

    fun handleImeValueChange(nextValue: TextFieldValue) {
        val delta = calculateImeDelta(imeBridgeValue.text, nextValue.text)
        repeat(delta.backspaceCount) { sendKey("Backspace") }
        if (delta.insertedText.isNotEmpty()) {
            webView?.sendVncText(delta.insertedText)
        }
        imeBridgeValue = nextValue
    }

    fun requestKeyboardForRemoteClick(x: Int, y: Int) {
        scope.launch {
            onShouldShowKeyboard(x, y)
                .onSuccess { shouldShow ->
                    if (!shouldShow) return@onSuccess
                    imeBridgeValue = newImeBridgeValue()
                    textFocusRequester.requestFocus()
                    delay(60)
                    keyboardController?.show()
                }
        }
    }

    BackHandler(enabled = imeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    BackHandler(enabled = !imeVisible, onBack = onBack)

    LaunchedEffect(Unit) {
        onStart()
            .onSuccess { viewerReady = true }
            .onFailure { errorMessage = it.message ?: "Unable to start Chrome." }
        isStarting = false
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AetherBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChromeToolbarButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.chrome_label),
                color = AetherOnSurface,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            ChromeToolbarButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.common_refresh),
                onClick = ::startOrRefresh,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (viewerReady) {
                ChromeVncWebView(
                    modifier = Modifier.fillMaxSize(),
                    onRemoteLeftClick = ::requestKeyboardForRemoteClick,
                    onCreated = { webView = it },
                )
                BasicTextField(
                    value = imeBridgeValue,
                    onValueChange = ::handleImeValueChange,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(textFocusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.Transparent,
                        fontSize = 1.sp,
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onDone = { submitInput() },
                        onGo = { submitInput() },
                        onNext = { submitInput() },
                        onSearch = { submitInput() },
                        onSend = { submitInput() },
                    ),
                )
            }
            if (isStarting) {
                CircularProgressIndicator(color = Color.White)
            } else if (!viewerReady) {
                Text(
                    text = errorMessage.ifBlank {
                        stringResource(R.string.chrome_vnc_unavailable)
                    },
                    color = AetherOnSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChromeVncWebView(
    modifier: Modifier,
    onRemoteLeftClick: (Int, Int) -> Unit,
    onCreated: (WebView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                addJavascriptInterface(
                    AlpineChromeJavascriptBridge(this, onRemoteLeftClick),
                    "AetherChromeBridge",
                )
                webViewClient = AlpineChromeWebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                excludeSystemNavigationGestures()
                loadUrl(AlpineChromeViewerUrl)
                onCreated(this)
            }
        },
    )
}

private class AlpineChromeJavascriptBridge(
    private val webView: WebView,
    private val onRemoteLeftClick: (Int, Int) -> Unit,
) {
    @JavascriptInterface
    fun onLeftClick(x: Int, y: Int) {
        webView.post { onRemoteLeftClick(x, y) }
    }
}

private class AlpineChromeWebViewClient : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(AlpineChromeViewportScript, null)
    }
}

private fun WebView.sendVncText(text: String) {
    evaluateJavascript(
        "window.aetherVncInput && window.aetherVncInput.sendText(${JSONObject.quote(text)});",
        null,
    )
}

private fun WebView.excludeSystemNavigationGestures() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    fun updateExclusionRect() {
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateExclusionRect() }
    post(::updateExclusionRect)
}

private fun WebView.sendVncKey(key: String) {
    evaluateJavascript(
        "window.aetherVncInput && window.aetherVncInput.sendKey(${JSONObject.quote(key)});",
        null,
    )
}

private fun newImeBridgeValue(): TextFieldValue = TextFieldValue(
    text = ImeBridgeSentinel,
    selection = TextRange(ImeBridgeSentinel.length),
)

private fun calculateImeDelta(previous: String, next: String): ImeDelta {
    var prefixLength = 0
    val sharedLength = minOf(previous.length, next.length)
    while (
        prefixLength < sharedLength &&
        previous[prefixLength] == next[prefixLength]
    ) {
        prefixLength += 1
    }

    var suffixLength = 0
    while (
        suffixLength < previous.length - prefixLength &&
        suffixLength < next.length - prefixLength &&
        previous[previous.length - suffixLength - 1] ==
        next[next.length - suffixLength - 1]
    ) {
        suffixLength += 1
    }

    val removedText = previous.substring(
        prefixLength,
        previous.length - suffixLength,
    )
    val insertedText = next.substring(
        prefixLength,
        next.length - suffixLength,
    )
    return ImeDelta(
        backspaceCount = removedText.codePointCount(0, removedText.length),
        insertedText = insertedText,
    )
}

private data class ImeDelta(
    val backspaceCount: Int,
    val insertedText: String,
)

private val AlpineChromeViewportScript = """
    (() => {
      let viewport = document.querySelector('meta[name="viewport"]');
      if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.prepend(viewport);
      }
      viewport.content =
        'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

      let style = document.getElementById('aether-mobile-style');
      if (!style) {
        style = document.createElement('style');
        style.id = 'aether-mobile-style';
        document.head.appendChild(style);
      }
      style.textContent = `
        html, body {
          width: 100% !important;
          margin: 0 !important;
          overflow: hidden !important;
          overscroll-behavior: none !important;
        }
        #top_bar {
          box-sizing: border-box !important;
          min-height: 24px !important;
          padding: 4px 8px !important;
          flex: 0 0 24px !important;
          font: 500 10px/16px sans-serif !important;
        }
        #status {
          display: block !important;
          overflow: hidden !important;
          text-overflow: ellipsis !important;
          white-space: nowrap !important;
        }
        #sendCtrlAltDelButton {
          display: none !important;
        }
        #screen {
          min-width: 0 !important;
          min-height: 0 !important;
          overflow: hidden !important;
          touch-action: none !important;
        }
        #screen canvas {
          touch-action: none !important;
        }
        body > canvas[style*="z-index: 65535"] {
          transform: scale(var(--aether-cursor-scale)) !important;
          transform-origin: top left !important;
          will-change: left, top, transform;
        }
      `;
      document.documentElement.style.setProperty(
        '--aether-cursor-scale',
        String(1 / Math.max(1, window.devicePixelRatio)),
      );

      const fitViewer = () => {
        const height = window.innerHeight + 'px';
        document.documentElement.style.height = height;
        document.body.style.height = height;
        window.dispatchEvent(new Event('resize'));
      };

      const installTouchpad = () => {
        const canvas = document.querySelector('#screen canvas');
        if (!canvas || canvas.width < 1 || canvas.height < 1) {
          return false;
        }
        if (canvas.dataset.aetherTouchpad === 'true') {
          return true;
        }
        canvas.dataset.aetherTouchpad = 'true';

        let cursorX = canvas.width / 2;
        let cursorY = canvas.height / 2;
        let maxTouches = 0;
        let startTime = 0;
        let startX = 0;
        let startY = 0;
        let lastX = 0;
        let lastY = 0;
        let moved = false;
        let pendingDeltaX = 0;
        let pendingDeltaY = 0;
        let moveFrame = 0;
        let pendingScrollX = 0;
        let pendingScrollY = 0;
        let scrollFrame = 0;
        let gestureActive = false;
        let leftDragActive = false;

        const centroid = touches => {
          let x = 0;
          let y = 0;
          for (const touch of touches) {
            x += touch.clientX;
            y += touch.clientY;
          }
          return {
            x: x / Math.max(1, touches.length),
            y: y / Math.max(1, touches.length),
          };
        };

        const stopTouch = event => {
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();
        };

        const mousePosition = () => {
          const rect = canvas.getBoundingClientRect();
          return {
            x: rect.left + cursorX * rect.width / Math.max(1, canvas.width),
            y: rect.top + cursorY * rect.height / Math.max(1, canvas.height),
          };
        };

        const dispatchMouse = (target, type, button, buttons) => {
          const position = mousePosition();
          target.dispatchEvent(new MouseEvent(type, {
            bubbles: true,
            cancelable: true,
            clientX: position.x,
            clientY: position.y,
            button,
            buttons,
          }));
        };

        const emitMouse = (type, button, buttons) => {
          dispatchMouse(canvas, type, button, buttons);
        };

        const releaseNoVncCapture = button => {
          const proxy = document.getElementById('noVNC_mouse_capture_elem');
          if (document.captureElement && proxy) {
            dispatchMouse(proxy, 'mouseup', button, 0);
          } else {
            emitMouse('mouseup', button, 0);
          }
        };

        const moveCursor = (deltaX, deltaY) => {
          const rect = canvas.getBoundingClientRect();
          cursorX = Math.max(
            0,
            Math.min(canvas.width - 1, cursorX + deltaX * canvas.width / Math.max(1, rect.width)),
          );
          cursorY = Math.max(
            0,
            Math.min(canvas.height - 1, cursorY + deltaY * canvas.height / Math.max(1, rect.height)),
          );
          emitMouse('mousemove', 0, leftDragActive ? 1 : 0);
        };

        const flushCursorMove = () => {
          if (moveFrame !== 0) {
            cancelAnimationFrame(moveFrame);
            moveFrame = 0;
          }
          if (pendingDeltaX === 0 && pendingDeltaY === 0) {
            return;
          }
          const nextDeltaX = pendingDeltaX;
          const nextDeltaY = pendingDeltaY;
          pendingDeltaX = 0;
          pendingDeltaY = 0;
          moveCursor(nextDeltaX, nextDeltaY);
        };

        const scheduleCursorMove = (deltaX, deltaY) => {
          pendingDeltaX += deltaX;
          pendingDeltaY += deltaY;
          if (moveFrame !== 0) {
            return;
          }
          moveFrame = requestAnimationFrame(() => {
            moveFrame = 0;
            flushCursorMove();
          });
        };

        const emitWheel = (deltaX, deltaY) => {
          const position = mousePosition();
          canvas.dispatchEvent(new WheelEvent('wheel', {
            bubbles: true,
            cancelable: true,
            clientX: position.x,
            clientY: position.y,
            deltaX,
            deltaY,
            deltaMode: 0,
          }));
        };

        const flushScroll = () => {
          if (scrollFrame !== 0) {
            cancelAnimationFrame(scrollFrame);
            scrollFrame = 0;
          }
          if (pendingScrollX === 0 && pendingScrollY === 0) {
            return;
          }
          const nextScrollX = pendingScrollX;
          const nextScrollY = pendingScrollY;
          pendingScrollX = 0;
          pendingScrollY = 0;
          emitWheel(-nextScrollX * 2, -nextScrollY * 2);
        };

        const scheduleScroll = (deltaX, deltaY) => {
          pendingScrollX += deltaX;
          pendingScrollY += deltaY;
          if (scrollFrame !== 0) {
            return;
          }
          scrollFrame = requestAnimationFrame(() => {
            scrollFrame = 0;
            flushScroll();
          });
        };

        const click = () => {
          emitMouse('mousemove', 0, 0);
          emitMouse('mousedown', 0, 1);
          releaseNoVncCapture(0);
        };

        const beginLeftDrag = () => {
          if (leftDragActive) {
            return;
          }
          emitMouse('mousemove', 0, 0);
          emitMouse('mousedown', 0, 1);
          leftDragActive = true;
        };

        const endLeftDrag = () => {
          if (!leftDragActive) {
            return;
          }
          flushCursorMove();
          releaseNoVncCapture(0);
          leftDragActive = false;
        };

        const sendNoVncGesture = type => {
          const position = mousePosition();
          canvas.dispatchEvent(new CustomEvent('gesturestart', {
            detail: {
              type,
              clientX: position.x,
              clientY: position.y,
            },
            bubbles: true,
            cancelable: true,
          }));
        };

        const resetGesture = () => {
          if (moveFrame !== 0) {
            cancelAnimationFrame(moveFrame);
          }
          if (scrollFrame !== 0) {
            cancelAnimationFrame(scrollFrame);
          }
          moveFrame = 0;
          scrollFrame = 0;
          pendingDeltaX = 0;
          pendingDeltaY = 0;
          pendingScrollX = 0;
          pendingScrollY = 0;
          endLeftDrag();
          maxTouches = 0;
          moved = false;
          gestureActive = false;
        };

        document.addEventListener('touchstart', event => {
          const position = centroid(event.touches);
          if (maxTouches === 0) {
            const rect = canvas.getBoundingClientRect();
            if (
              position.x < rect.left ||
              position.x > rect.right ||
              position.y < rect.top ||
              position.y > rect.bottom
            ) {
              return;
            }
            if (document.captureElement) {
              releaseNoVncCapture(0);
            }
            gestureActive = true;
            startTime = performance.now();
            moved = false;
          }
          if (!gestureActive) {
            return;
          }
          stopTouch(event);
          maxTouches = Math.max(maxTouches, event.touches.length);
          startX = position.x;
          startY = position.y;
          lastX = position.x;
          lastY = position.y;
        }, { capture: true, passive: false });

        document.addEventListener('touchmove', event => {
          if (!gestureActive) {
            return;
          }
          stopTouch(event);
          const position = centroid(event.touches);
          const deltaX = position.x - lastX;
          const deltaY = position.y - lastY;
          if (Math.hypot(position.x - startX, position.y - startY) > 8) {
            moved = true;
          }
          if (maxTouches === 1 && event.touches.length === 1) {
            scheduleCursorMove(deltaX, deltaY);
          } else if (maxTouches === 2 && event.touches.length === 2) {
            scheduleScroll(deltaX, deltaY);
          } else if (maxTouches === 3 && event.touches.length === 3 && moved) {
            beginLeftDrag();
            scheduleCursorMove(deltaX, deltaY);
          }
          lastX = position.x;
          lastY = position.y;
        }, { capture: true, passive: false });

        document.addEventListener('touchend', event => {
          if (!gestureActive) {
            return;
          }
          stopTouch(event);
          if (leftDragActive && event.touches.length < 3) {
            endLeftDrag();
          }
          if (event.touches.length > 0) {
            const position = centroid(event.touches);
            lastX = position.x;
            lastY = position.y;
            return;
          }
          flushCursorMove();
          flushScroll();
          const isTap = !moved && performance.now() - startTime <= 450;
          if (isTap && maxTouches === 1) {
            click();
            if (cursorY >= 34 && cursorY <= 100) {
              window.setTimeout(() => {
                emitKey('keydown', 'Control', 'ControlLeft', 17);
                emitKey('keydown', 'a', 'KeyA', 65);
                emitKey('keyup', 'a', 'KeyA', 65);
                emitKey('keyup', 'Control', 'ControlLeft', 17);
              }, 40);
            }
            if (window.AetherChromeBridge) {
              window.AetherChromeBridge.onLeftClick(
                Math.round(cursorX),
                Math.round(cursorY),
              );
            }
          } else if (isTap && maxTouches === 2) {
            sendNoVncGesture('twotap');
          }
          maxTouches = 0;
          gestureActive = false;
        }, { capture: true, passive: false });

        document.addEventListener('touchcancel', event => {
          if (!gestureActive) {
            return;
          }
          stopTouch(event);
          resetGesture();
        }, { capture: true, passive: false });

        window.addEventListener('blur', resetGesture);

        const keyDetails = {
          Backspace: { code: 'Backspace', keyCode: 8 },
          Delete: { code: 'Delete', keyCode: 46 },
          Enter: { code: 'Enter', keyCode: 13 },
          Tab: { code: 'Tab', keyCode: 9 },
          ArrowLeft: { code: 'ArrowLeft', keyCode: 37 },
          ArrowUp: { code: 'ArrowUp', keyCode: 38 },
          ArrowRight: { code: 'ArrowRight', keyCode: 39 },
          ArrowDown: { code: 'ArrowDown', keyCode: 40 },
        };

        const emitKey = (type, key, code, keyCode) => {
          canvas.dispatchEvent(new KeyboardEvent(type, {
            key,
            code,
            keyCode,
            which: keyCode,
            bubbles: true,
            cancelable: true,
          }));
        };

        const sendKey = key => {
          const details = keyDetails[key] || {
            code: 'Unidentified',
            keyCode: key.length === 1 ? key.toUpperCase().charCodeAt(0) : 0,
          };
          emitKey('keydown', key, details.code, details.keyCode);
          emitKey('keyup', key, details.code, details.keyCode);
        };

        window.aetherVncInput = {
          sendKey,
          sendText(text) {
            for (const character of text) {
              if (character === '\n') {
                sendKey('Enter');
              } else {
                sendKey(character);
              }
            }
          },
        };
        return true;
      };

      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          fitViewer();
          if (!installTouchpad()) {
            const interval = window.setInterval(() => {
              if (installTouchpad()) {
                window.clearInterval(interval);
              }
            }, 100);
          }
        });
      });
    })();
""".trimIndent()

@Composable
private fun ChromeToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(AetherSurface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AetherOnSurface,
            modifier = Modifier.size(21.dp),
        )
    }
}
