(ns com.msladecek.inspector.protocols)

(defprotocol Viewer
  (display [viewer data]))
