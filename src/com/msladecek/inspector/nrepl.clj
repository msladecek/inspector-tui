(ns com.msladecek.inspector.nrepl
  (:require
   [cider.nrepl.pprint :as cider-pprint]
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [nrepl.cmdline :as cmdline]
   [nrepl.middleware :as m]
   [nrepl.middleware.print]
   [com.msladecek.inspector :as inspector]))

(defn print-replacement [value writer options]
  (try
    (inspector/send-data!
      (get options :com.msladecek.inspector.nrepl/middleware-options {})
      value)
    (catch Exception _))
  (cider-pprint/pprint value writer options))

(defn middleware [next-handler]
  (fn [message]
    (-> message
      (assoc
        :nrepl.middleware.print/print #'print-replacement
        :nrepl.middleware.caught/print? true)
      (next-handler))))

(m/set-descriptor! #'middleware {:expects #{#'nrepl.middleware.print/wrap-print}})

(defn -main [& args]
  (if (some #{"-h" "--help"} args)
    (let [inspector-usage (-> args
                            (cli/parse-opts inspector/cli-options)
                            :summary
                            (inspector/usage))
          nrepl-usage (cmdline/help)]
      (->> ["Usage: inspector.nrepl [NREPL-OPTIONS] [--] [INSPECTOR-OPTIONS]"
            ""
            "Utility that launches an nrepl server and an inspector viewer at the same time."
            "NREPL-OPTIONS are passed to nrepl.cmdline/-main."
            "INSPECTOR-OPTIONS are passed to com.msladecek.inspector/-main."
            "Only the -h/--help option is overriden (it will print this help message and exit)."
            ""
            "The nrepl options will automatically be configured with cider.nrepl/cider-middleware and com.msladecek.inspector.nrepl/middleware."
            ""
            "Below are the usage docs for nrepl.cmdline/-main and com.msladecek.inspector/-main:"
            ""]
        (string/join "\n")
        (print))
      (println)
      (println "nrepl.cmdline/-main")
      (println nrepl-usage)
      (println)
      (println "com.msladecek.inspector/-main")
      (print inspector-usage)
      (println)
      (System/exit 0))
    (let [[nrepl-args _ inspector-args] (partition-by #{"--"} args)
          nrepl-args-with-middleware (concat nrepl-args
                                       ["--middleware" "[cider.nrepl/cider-middleware com.msladecek.inspector.nrepl/middleware]"])
          nrepl-thread (Thread/new #(apply cmdline/-main nrepl-args-with-middleware))]
      (.start nrepl-thread)
      (apply inspector/-main inspector-args)
      (.join nrepl-thread))))
