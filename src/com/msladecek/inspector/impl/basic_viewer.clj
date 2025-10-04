(ns com.msladecek.inspector.impl.basic-viewer
  (:require
    [clojure.core.match :refer [match]]
    [clojure.datafy :refer [datafy]]
    [clojure.pprint :as pprint]
    [clojure.spec.alpha :as spec]
    [clojure.string :as string]
    [clojure.walk :as walk]
    [com.msladecek.inspector.protocols :as proto]))

(def reset-seq "\033[0m")
(def clear-screen-seq "\033[2J")
(defn move-cursor-seq [row column]
  (format "\033[%d;%dH" row column))

(spec/def ::any (constantly true))
(spec/def ::table (spec/coll-of map?))
(spec/def ::table-recursive (spec/or :table (spec/coll-of
                                              (spec/map-of ::any ::table-recursive :min-count 1)
                                              :min-count 1)
                                     :any ::any))

(defn default-representation [data]
  (cond
    (spec/valid? ::table-recursive data) :table-recursive
    (spec/valid? ::table data) :table
    :else :default))

(defmulti print-data :representation)

(defmethod print-data :table [view]
  (let [all-keys (->> (:data view)
                      (map keys)
                      (apply concat)
                      (into #{})
                      (sort))]
    (pprint/print-table all-keys (:data view))))

(defn -left-pad [width value]
  (format (str "%" width "s")  value))

(defn -surround-with [surround value]
  (str surround value surround))

(defmethod print-data :table-recursive [view]
  (->> (:data view)
       (spec/conform ::table-recursive)
       (walk/postwalk
        (fn [value]
          (match value
                 [:any value-]
                 (let [value-strs (-> (pprint/pprint value-)
                                      (with-out-str)
                                      (string/split-lines))]
                   {:value-strs value-strs
                    :rows (count value-strs)
                    :columns (->> value-strs (map count) (apply max 0))})

                 [:table table-rows]
                 (let [column-vertical-separator  " │ "
                       row-horizontal-separator "─"
                       row-separator-crossing "─┼─"
                       header-horizontal-separator "═"
                       header-separator-crossing "═╪═"
                       table-columns (->> table-rows
                                          (mapcat keys)
                                          (into #{})
                                          (sort))
                       column-widths (->> (for [col table-columns]
                                            [col (->> table-rows
                                                      (map #(get-in % [col :columns]))
                                                      (filter identity)
                                                      (apply max (count (str col))))])
                                          (into {}))
                       header (->> table-columns
                                   (map #(-left-pad (column-widths %) %))
                                   (string/join column-vertical-separator)
                                   (-surround-with " "))
                       header-separator (->> (for [col table-columns]
                                               (->> (repeat (column-widths col) header-horizontal-separator)
                                                    (apply str)))
                                             (string/join header-separator-crossing)
                                             (-surround-with header-horizontal-separator))
                       row-separator (->> (for [col table-columns]
                                               (->> (repeat (column-widths col) row-horizontal-separator)
                                                    (apply str)))
                                          (string/join row-separator-crossing)
                                          (-surround-with row-horizontal-separator))
                       value-rows (->> (for [row table-rows]
                                         (let [row-total-height (->> (vals row)
                                                                     (map :rows)
                                                                     (apply max 1))]
                                           (for [subrow-no (range row-total-height)]
                                             (->> (for [col table-columns]
                                                    (let [subrow (get-in row [col :value-strs subrow-no] "")]
                                                      (-left-pad (column-widths col) subrow)))
                                                  (string/join column-vertical-separator)
                                                  (-surround-with " ")))))
                                       (interpose [row-separator])
                                       (apply concat))
                       value-strs (into [header header-separator] value-rows)]
                   {:value-strs value-strs
                    :rows (count value-strs)
                    :columns (->> value-strs (map count) (apply max 0))})

                 :else
                 value)))
       :value-strs
       (string/join "\n")
       (print)))

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
  ;; TODO: fix multiline strings, eg. (tap> (with-out-str (clojure.repl/doc locking)))
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

(comment
  (require '[clojure.repl.deps :refer [sync-deps]])
  (sync-deps)

  (def data [{:a 1 :b "potato" :c "green" :d [{:e "dog" :f "giraffe"}
                                              {:e "cat" :g "car"}]}
             {:a 100 :b "carrot"}
             {:c "red" :d [{:i "eye" :j [{:k "kay" :l :elle}
                                         {:k "cocoa"}]}]}])


  (def sample {:representation :table-recursive
               :data data})
  (tap> data)

  (print-data sample)

  (pprint/print-table [{:a "hello\nworld" :b "potato"}
                       {:a "dog"}])

  (require '[clojure.repl :refer [doc]])

  (tap> (with-out-str (doc locking)))

  ;;
  )
