(ns com.msladecek.inspector.impl.basic-viewer
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.pprint :as pprint]
    [clojure.spec.alpha :as spec]
    [clojure.string :as string]
    [com.msladecek.inspector.protocols :as proto]))

(def reset-seq "\033[0m")
(def clear-screen-seq "\033[2J")
(defn move-cursor-seq [row column]
  (format "\033[%d;%dH" row column))

(spec/def ::table (spec/coll-of map?))

(defn default-representation [data]
  (if (spec/valid? ::table data)
    :table
    :default))

(defmulti print-data :representation)

(defmethod print-data :table [view]
  (let [all-keys (->> (:data view)
                      (map keys)
                      (apply concat)
                      (into #{})
                      (sort))]
    (pprint/print-table all-keys (:data view))))

(defmethod print-data :default [view]
  (-> (:data view)
      (datafy)
      (pprint/pprint)))

(def next-representation
  (let [representations (keys (methods print-data))]
    (->> (map vector representations (next (cycle representations)))
         (into {}))))

(def previous-representation
  (->> next-representation
       (map (fn [[r1 r2]] [r2 r1]))
       (into {})))

(defn print-state [{:keys [show-help views selected-view-idx]}]
  ;; TODO: fix broken output when a bunch of data is submitted at once
  ;; TODO: draw boxes around the various components
  ;;   /--------------------------\
  ;;   | help text (when enabled) |
  ;;   \--------------------------/
  ;;   /--------------------------\
  ;;   | view n/N                 |
  ;;   |--------------------------|
  ;;   | data representation      |
  ;;   \--------------------------/

  (print reset-seq clear-screen-seq (move-cursor-seq 1 1))
  (when show-help
    (println (->> ["Keybindings:"
                   "  ?      Toggle help"
                   "  X      Delete the view from the list (no confirmation)"
                   "  H, L   Switch view (backwards, forwards)"
                   "  [, ]   Cycle view representations (backwards, forwards)"
                   ""]
                  (string/join "\n"))))
  (let [view (get views selected-view-idx)]
    (println (format "view %s/%d%s"
                     (if (nil? selected-view-idx) "-" (str (inc selected-view-idx)))
                     (count views)
                     (if view (str " as " (-> view :representation name)) "")))
    (cond
      (nil? selected-view-idx)
      (println "no view selected")

      (<= (count views) selected-view-idx)
      (println (format "invalid selected view %d, only %d views available"
                       (inc selected-view-idx) (count views)))

      :else
      (try
        (print-data view)
        (catch Exception _
          (println (format "ERROR: failed to represent the data as %s, displaying instead the default representation:"
                           (-> view :representation name)))
          (print-data (assoc view :representation :default)))))))

(defrecord BasicViewer [state]
  proto/Viewer
  (display [_ data]
    (swap! state (fn [{:keys [views] :as state}]
                   (-> state
                       (update :views conj {:data data :representation (default-representation data)})
                       (assoc :selected-view-idx (count views)))))
    (print-state @state)
    true)

  (on-key [_ key]
    (swap! state
           (fn [{:keys [selected-view-idx views] :as state}]
             (cond
               (= \? key)
               (update state :show-help not)

               (nil? selected-view-idx)
               state

               (and (= \H key) (< 0 selected-view-idx))
               (update state :selected-view-idx dec)

               (and (= \L key) (< selected-view-idx (dec (count views))))
               (update state :selected-view-idx inc)

               (= \[ key)
               (update-in state [:views selected-view-idx :representation] previous-representation)

               (= \] key)
               (update-in state [:views selected-view-idx :representation] next-representation)

               (= \X key)
               (assoc state
                      :views (->> views
                                  (keep-indexed (fn [idx view]
                                                  (when (not= selected-view-idx idx)
                                                    view)))
                                  (into []))
                      :selected-view-idx (cond
                                           (= 1 (count views))
                                           nil

                                           (= (dec (count views)) selected-view-idx)
                                           (dec selected-view-idx)

                                           :else
                                           selected-view-idx))

               :else
               state)))))

(defn make-basic-viewer
  ([]
   (let [state (atom {})]
     (add-watch state :printer
                (fn [_key _state-atom previous-state new-state]
                  (when-not (= previous-state new-state)
                    (print-state new-state))))
     (reset! state {:views []})
     (->BasicViewer state)))
   ([_opts]
    (make-basic-viewer)))
