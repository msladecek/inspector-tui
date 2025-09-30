(ns com.msladecek.inspector
  (:require
    [clojure.string :as str]
    [clojure.java.shell :as shell]
    [aleph.tcp :as tcp]
    [clojure.edn :as edn]
    [gloss.core :as gloss]
    [gloss.io :as io]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [com.msladecek.inspector.protocols :as proto]
    [com.msladecek.inspector.impl.basic-viewer :refer [make-basic-viewer]]))

(defn -ignore-tag [tag value] value)

(defn -try-to-read-string [string-value]
  (try
    (edn/read-string {:eof nil :default -ignore-tag}
                     string-value)
    (catch Exception _
      string-value)))

(def byte-protocol
  (gloss/compile-frame
    (gloss/finite-frame :uint32
      (gloss/string :utf-8))
    pr-str
    -try-to-read-string))

(defn wrap-duplex-stream [protocol stream]
  (let [out (s/stream)]
    (s/connect (s/map #(io/encode protocol %) out) stream)
    (s/splice out (io/decode-stream stream protocol))))

(defn start-tcp-server [handler port]
  (tcp/start-server (fn [stream info]
                      (handler (wrap-duplex-stream byte-protocol stream) info))
                    {:port port}))

(defn start-tcp-client [host port]
  (d/chain (tcp/client {:host host :port port})
           #(wrap-duplex-stream byte-protocol %)))

(defn inspector-handler [stream info viewer]
  (d/loop []
    (-> (d/let-flow [message (s/take! stream ::none)]
          (when-not (= ::none message)
            (let [success (proto/display viewer message)
                  response (if success "ok" "not-ok")]
              (d/let-flow [result (s/put! stream response)]
                (when result (d/recur))))))
        (d/catch (fn [ex]
                   (s/put! stream (str "ERROR: " ex))
                   (s/close! stream))))))

(defn stty! [args]
  (-> (shell/sh "sh" "-c" (str "stty " args " < /dev/tty"))
      :out
      (str/trim)))

(defmacro with-char-input
  "Set the tty configuration to give us one character at a time."
  [& body]
  `(let [initial-config# (stty! "--save")]
     (try
       (stty! "-icanon min 1")
       (stty! "-echo")
       ~@body
       (finally
         (stty! initial-config#)))))

(defn start-input-loop [viewer]
  (with-char-input
    (with-open [in System/in]
      (loop []
        (let [char-value (.read in)]
          (proto/on-key viewer (char char-value)))
        (recur)))))

(defn -main [& args]
  (when (= ["start-viewer"] args)
    (let [viewer (make-basic-viewer)
          publisher-thread (Thread/new #(start-tcp-server
                                          (fn [stream info]
                                            (inspector-handler stream info viewer))
                                          10001))
          input-thread (Thread/new #(start-input-loop viewer))]
      (.start publisher-thread)
      (.start input-thread)
      (.join publisher-thread)
      (.join input-thread))))

(def client (atom nil))

(defn send-data! [value]
  ;; TODO: this should be a future?
  ;; TODO: automatically reconnect

  ;; (require '[com.msladecek.inspector :as inspector])
  ;; (add-tap inspector/send-data!)

  (when (nil? @client)
    (reset! client @(start-tcp-client "localhost" 10001)))

  (let [c @client]
    @(s/put! c value)
    @(s/take! c)))

(comment
  (send-data! "potato")
  (send-data! [1 2 3])
  (send-data! [{:a 1 :b "potato" :c "green"}
               {:a 100 :b "carrot"}
               {:c "red"}])
  )
