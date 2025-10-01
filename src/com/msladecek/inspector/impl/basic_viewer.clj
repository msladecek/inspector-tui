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

(defn print-sections
  ([sections]
   (print-sections {} sections))
  ([{:keys [width height]} sections]
   (let [content-lines (->> sections
                            (map #(string/split-lines (with-out-str (print %))))
                            (interpose [:separator])
                            (apply concat)
                            (into []))
         inner-width (if width
                       (- width 2)
                       (->> content-lines
                            (filter #(not= :separator %))
                            (map count)
                            (apply max 0)))
         inner-height (if height
                        (- height 2)
                        (count content-lines))
         plan-lines (->> content-lines
                         (#(concat % (repeat (- inner-height (count %)) "")))
                         (take inner-height)
                         (into []))]
     (when-not (or (< inner-height 0) (< inner-width 0))
       (print "┏")
       (print (apply str (repeat inner-width "━")))
       (println "┓")
       (doseq [line plan-lines]
         (if (= :separator line)
           (do
             (print "┠")
             (print (apply str (repeat inner-width "─")))
             (println "┨"))
           (do
             (print "┃")
             (print (subs line 0 (min (count line) inner-width)))
             (print (apply str (repeat (- inner-width (count line)) " ")))
             (println "┃"))))
       (print "┗")
       (print (apply str (repeat inner-width "━")))
       (print "┛")))))

(defn print-state [{:keys [size show-help views selected-view-idx]}]
  ;; TODO: fix broken output when a bunch of data is submitted at once
  (let [help-content (->> ["Keybindings:"
                           "  ?      Toggle help"
                           "  X      Delete the selected view (no confirmation)"
                           "  H, L   Switch view (backwards, forwards)"
                           "  [, ]   Cycle representations (backwards, forwards)"
                           ""]
                          (string/join "\n"))
        view (get views selected-view-idx)
        preamble (format "view %s/%d%s"
                         (if (nil? selected-view-idx) "-" (str (inc selected-view-idx)))
                         (count views)
                         (if view (str " as " (-> view :representation name)) ""))
        content (with-out-str
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
                        (print-data (assoc view :representation :default))))))
        sections (->> [(when show-help help-content) preamble content]
                      (filterv identity))]
    (print clear-screen-seq (move-cursor-seq 1 1))
    (print-sections (update size :height (fnil dec 1)) sections)
    (println)
    (print "Toggle help with [?]")
    (flush)))

(defrecord BasicViewer [state]
  proto/Viewer
  (set-size [_ size]
    (swap! state assoc :size size))

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
