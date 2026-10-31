# WhatsApp confirmed-send pending session correction

- Only one WhatsApp confirmed-send attempt can be armed at once.
- Every delayed poll carries a generation token; canceled or superseded sessions cannot click Send.
- The pending attempt is canceled on timeout, successful ACTION_CLICK, accessibility service destruction, or failure to launch WhatsApp.
- No delivery receipt is claimed; Android device tests and APK compilation are still outstanding.
- GGUF and original multimedia integrations unchanged.
