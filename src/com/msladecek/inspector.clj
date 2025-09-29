(ns com.msladecek.inspector
  (:require
    [aleph.tcp :as tcp]
    [clojure.datafy :refer [datafy]]
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint print-table]]
    [clojure.spec.alpha :as spec]
    [gloss.core :as gloss]
    [gloss.io :as io]
    [manifold.deferred :as d]
    [manifold.stream :as s]))

(def byte-protocol
  (gloss/compile-frame
    (gloss/finite-frame :uint32
      (gloss/string :utf-8))
    pr-str
    edn/read-string))

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

(def reset-seq "\033[0m")
(def clear-screen-seq "\033[2J")
(defn move-cursor-seq [row column]
  (format "\033[%d;%dH" row column))

(spec/def ::table (spec/coll-of map?))

(defn draw-to-screen [data]
  (print reset-seq clear-screen-seq (move-cursor-seq 1 1))
  (cond
    (spec/valid? ::table data)
    (let [all-keys (->> data
                        (map keys)
                        (apply concat)
                        (into #{})
                        sort)]
      (print-table all-keys data))

    :else
    (pprint (datafy data)))
  true)

(defn inspector-handler [stream info]
  (d/loop []
    (-> (d/let-flow [message (s/take! stream ::none)]
          (when-not (= ::none message)
            (let [success (draw-to-screen message)
                  response (if success "ok" "not-ok")]
              (d/let-flow [result (s/put! stream response)]
                (when result (d/recur))))))
        (d/catch (fn [ex]
                   (s/put! stream (str "ERROR: " ex))
                   (s/close! stream))))))

(def client (atom nil))

(defn send-data! [value]
  ;; (require '[com.msladecek.inspector :as inspector])
  ;; (add-tap inspector/send-data!)
  (when (nil? @client)
    (reset! client @(start-tcp-client "localhost" 10001)))

  (let [c @client]
    @(s/put! c value)
    @(s/take! c)))


;; TODO: nrepl middleware

(defn -main [& args]
  ;; run tcp server as a viewer
  (when (= ["start-server"] args)
    (start-tcp-server
      (fn [stream info]
        (inspector-handler stream info))
      10001)))

(comment
  (send-data! "potato")
  (send-data! [1 2 3])
  (send-data! [{:a 1 :b "potato" :c "green"}
               {:a 100 :b "carrot"}
               {:c "red"}])
  )
