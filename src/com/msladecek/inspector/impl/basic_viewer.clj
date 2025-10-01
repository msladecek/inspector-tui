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

(defn draw-state-to-screen [{:keys [views selected-view-idx]}]
  (print reset-seq clear-screen-seq (move-cursor-seq 1 1))
  (let [data (get views selected-view-idx)]
    (println (format "view %s/%d"
                     (if (nil? selected-view-idx) "-" (str (inc selected-view-idx)))
                     (count views)))
    (cond
      (nil? selected-view-idx)
      (println "no view selected")

      (<= (count views) selected-view-idx)
      (println (format "invalid selected view %d, only %d views available"
                       (inc selected-view-idx) (count views)))

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
    (swap! state (fn [{:keys [views] :as state}]
                   (-> state
                       (update :views conj data)
                       (assoc :selected-view-idx (count views)))))
    (draw-state-to-screen @state)
    true)

  (on-key [_ key]
    (when (#{\H \L} key)
      (swap! state (fn [{:keys [selected-view-idx views] :as state}]
                     (cond
                       (nil? selected-view-idx)
                       state

                       (and (= \H key) (< 0 selected-view-idx))
                       (update state :selected-view-idx dec)

                       (and (= \L key) (< selected-view-idx (dec (count views))))
                       (update state :selected-view-idx inc)

                       :else
                       state)))
      (draw-state-to-screen @state))))

(defn make-basic-viewer
  ([]
   (let [viewer (->BasicViewer (atom {:views []}))]
     (draw-state-to-screen @(:state viewer))
     viewer))
   ([_opts]
    (make-basic-viewer)))
