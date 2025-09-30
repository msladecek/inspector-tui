(ns com.msladecek.inspector.protocols)

(defprotocol Viewer
  (display [viewer data])
  (on-key [viewer key]))
