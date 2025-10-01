(ns com.msladecek.inspector.nrepl
  (:require
    [cider.nrepl.pprint :as cider-pprint]
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
        (assoc :nrepl.middleware.print/print #'print-replacement)
        (next-handler))))

(m/set-descriptor! #'middleware {:expects #{#'nrepl.middleware.print/wrap-print}})
