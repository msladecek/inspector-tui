(ns com.msladecek.inspector.impl.basic-viewer
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.pprint :refer [pprint print-table]]
    [clojure.spec.alpha :as spec]
    [com.msladecek.inspector.protocols :as proto]))

(def reset-seq "\033[0m")
(def clear-screen-seq "\033[2J")
(defn move-cursor-seq [row column]
  (format "\033[%d;%dH" row column))

(spec/def ::table (spec/coll-of map?))

(defn draw-state-to-screen [state]
  (print reset-seq clear-screen-seq (move-cursor-seq 1 1))
  (let [{:keys [views selected-view-idx]} state
        data (get views selected-view-idx)]
    (println (format "view %s/%d"
                     (if (nil? selected-view-idx) "-" (str (inc selected-view-idx)))
                     (count views)))
    (cond
      (nil? selected-view-idx)
      (println "no view selected")

      (<= (count views) selected-view-idx)
      (println (format "invalid selected view %d (zero-indexed), only %d views available"
                       selected-view-idx (count views)))

      (spec/valid? ::table data)
      (let [all-keys (->> data
                          (map keys)
                          (apply concat)
                          (into #{})
                          sort)]
        (print-table all-keys data))

      :else
      (pprint (datafy data)))))

(defrecord BasicViewer [state]
  proto/Viewer
  (display [_ data]
    (swap! state (fn [state]
                   (-> state
                       (update :views conj data)
                       (update :selected-view-idx (fnil inc -1)))))
    (draw-state-to-screen @state)
    true))

(defn make-basic-viewer []
  (let [viewer (->BasicViewer (atom {:views []}))]
    (draw-state-to-screen @(:state viewer))
    viewer))
