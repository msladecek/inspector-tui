(ns com.msladecek.inspector.protocols)

(defprotocol Transport
  (submit [transport data])
  (make-receiver [transport handler]))

(defprotocol Viewer
  (set-size [viewer size])
  (display [viewer data])
  (on-key [viewer key]))
