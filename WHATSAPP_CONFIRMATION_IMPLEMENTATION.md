# WhatsApp confirmation wiring (2026-10-30)

The OS screen now shows a recipient-and-message confirmation dialog before arming an Accessibility action. The Accessibility service polls the active WhatsApp window for up to 20 seconds, checks that the composer contains the *exact* confirmed text, and clicks the exact WhatsApp send resource ID. This is a single-use attempt and does not prove delivery. If the WhatsApp version uses different resource IDs, the action fails closed. Other chat/agent routes still only prepare the message and do not auto-send.

Static source verification passed. Android compilation and on-device behavior were not tested. GGUF was not modified.
