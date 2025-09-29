(ns com.msladecek.inspector.nrepl
  (:require
    [cider.nrepl.pprint :as cider-pprint]
    [clojure.pprint :refer [pprint print-table]]
    [com.msladecek.inspector :refer [send-data!]]
    [nrepl.middleware :as m]
    [nrepl.middleware.print]))

(defn print-replacement [value writer options]
  (send-data! value)
  (cider-pprint/pprint value writer options))

(defn middleware [next-handler]
  (fn [message]
    (-> message
        (assoc :nrepl.middleware.print/print #'print-replacement)
        (next-handler))))

(m/set-descriptor! #'middleware {:expects #{#'nrepl.middleware.print/wrap-print}})
