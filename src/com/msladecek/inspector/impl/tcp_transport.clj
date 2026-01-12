(ns com.msladecek.inspector.impl.tcp-transport
  (:require
   [aleph.tcp :as tcp]
   [clojure.edn :as edn]
   [gloss.core :as gloss]
   [gloss.io :as io]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [com.msladecek.inspector.protocols :as proto]))

(def default-connection-opts
  {:port 10001
   :host "localhost"})

(defn -ignore-tag [_tag value] value)

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

(defn handler-loop [handler]
  (fn [stream _info]
    (d/loop []
      (-> (d/let-flow [message (s/take! stream ::none)]
            (when-not (= ::none message)
              (let [success (handler message)
                    response (if success "ok" "not-ok")]
                (d/let-flow [result (s/put! stream response)]
                  (when result (d/recur))))))
        (d/catch (fn [ex]
                   (s/put! stream (str "ERROR: " ex))
                   (s/close! stream)))))))

(defn start-tcp-server [handler port]
  (let [handler-loop- (handler-loop handler)]
    (tcp/start-server (fn [stream info]
                        (-> (wrap-duplex-stream byte-protocol stream)
                          (handler-loop- info)))
      {:port port})))

(defn start-tcp-client [host port]
  (d/chain (tcp/client {:host host :port port})
    #(wrap-duplex-stream byte-protocol %)))

(defrecord TCPTransport [host port]
  proto/Transport
  (submit [_ data]
    (let [client-stream @(start-tcp-client host port)]
      @(s/put! client-stream data)))

  (make-receiver [_ handler]
    (start-tcp-server handler port)))

(defn make-tcp-transport
  ([]
   (make-tcp-transport default-connection-opts))
  ([opts]
   (let [merged-opts (merge default-connection-opts opts)]
     (->TCPTransport (:host merged-opts) (:port merged-opts)))))
