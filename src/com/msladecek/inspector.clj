(ns com.msladecek.inspector
  (:require
    [clojure.java.shell :as shell]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [com.msladecek.inspector.protocols :as proto]
    [com.msladecek.inspector.impl.basic-viewer :refer [make-basic-viewer]]
    [com.msladecek.inspector.impl.tcp-transport :as tcp-transport]))

(defn stty! [args]
  (-> (shell/sh "sh" "-c" (str "stty " args " < /dev/tty"))
      :out
      (string/trim)))

(defn get-terminal-size []
  (let [[height width] (-> (stty! "size")
                           (string/split #" ")
                           (->> (map parse-long)))]
    {:height height
     :width width}))

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
          (proto/set-size viewer (get-terminal-size))
          (proto/on-key viewer (char char-value)))
        (recur)))))

(defn validate-set-of-keywords [permitted-values]
  [permitted-values (->> permitted-values
                         (map name)
                         (sort)
                         (string/join ", ")
                         (str "Value must be one of: "))])

(def default-opts {:transport :tcp})

(def cli-options
  [[nil "--viewer STRING" "The kind of viewer which will be launched"
    :default :basic
    :default-desc "basic"
    :parse-fn keyword
    :validate (validate-set-of-keywords #{:basic})]
   [nil "--transport STRING" "Transport protocol which will be used to send data to the viewer"
    :default (-> default-opts :transport)
    :default-desc (-> default-opts :transport name)
    :parse-fn keyword
    :validate (validate-set-of-keywords #{:tcp})]
   [nil "--port PORT" "Used in configuring the transport"
    :default (:port tcp-transport/default-connection-opts)
    :parse-fn parse-long
    :validate [#(< 0 % 0x10000) "Port must be a number between 0 and 65536"]]
   ["-h" "--help" "Print the help message and exit"]])

(defn make-transport [opts]
  (let [constructor (case (:transport opts)
                      :tcp tcp-transport/make-tcp-transport)]
    (constructor opts)))

(defn make-viewer [opts]
  (let [constructor (case (:viewer opts)
                      :basic make-basic-viewer)
        viewer (constructor opts)]
    (proto/set-size viewer (get-terminal-size))
    viewer))

(defn usage [options-summary]
  (->> ["Usage: inspector [OPTIONS]"
        ""
        "Options:"
        options-summary
        ""
        "For more info, see <https://github.com/msladecek/inspector-tui>"]
       (string/join "\n")))

(defn -main [& args]
  (let [opts (cli/parse-opts args cli-options)
        usage-str (-> opts :summary usage)
        errors (-> opts :errors)]
    (cond
      (seq errors)
      (binding [*out* *err*]
        (println (string/join "\n" errors))
        (println usage-str)
        (System/exit 1))

      (-> opts :options :help)
      (do
        (println usage-str)
        (System/exit 0))

      :else
      (let [viewer (make-viewer (-> opts :options))
            transport (make-transport (-> opts :options))
            viewer-thread (Thread/new #(proto/make-receiver
                                         transport
                                         (fn [message]
                                           (proto/display viewer message))))
            input-thread (Thread/new #(start-input-loop viewer))]
        (.start viewer-thread)
        (.start input-thread)
        (.join viewer-thread)
        (.join input-thread)))))

(defn send-data!
  "Send data to the viewer.
  Usage with `tap>`:

      (require '[com.msladecek.inspector :as inspector])
      (add-tap inspector/send-data!)

  "
  ([value]
   (send-data! {} value))
  ([opts value]
   (let [transport (make-transport (merge default-opts opts))]
     (proto/submit transport value))))

(comment
  (send-data! "potato")
  (send-data! [1 2 3])
  (send-data! [{:a 1 :b "potato" :c "green"}
               {:a 100 :b "carrot"}
               {:c "red"}])

  (send-data! {:port 10003} "potato")
  (send-data! {:port 10003} [1 2 3])
  (send-data! {:port 10003} [{:a 1 :b "potato" :c "green"}
                             {:a 100 :b "carrot"}
                             {:c "red"}])

  )
