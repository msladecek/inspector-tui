(ns com.msladecek.inspector.nrepl
  (:require
    [cider.nrepl.pprint :as cider-pprint]
    [clojure.pprint :refer [pprint print-table]]
    [nrepl.middleware :as m]
    [nrepl.middleware.print]
    [com.msladecek.inspector :as inspector]))

(defn print-replacement [value writer options]
  (inspector/send-data!
    (get options :com.msladecek.inspector.nrepl/middleware-options {})
    value)
  (cider-pprint/pprint value writer options))

(defn middleware [next-handler]
  (fn [message]
    (-> message
        (assoc :nrepl.middleware.print/print #'print-replacement)
        (next-handler))))

(m/set-descriptor! #'middleware {:expects #{#'nrepl.middleware.print/wrap-print}})
