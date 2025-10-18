(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

;; https://github.com/seancorfield/deps-new
; clojure -A:deps -T:build help/doc

(def lib 'com.msladecek/inspector)
(def version (format "0.5.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")

(defn test "Run all the tests." [opts]
  (let [basis (b/create-basis {:aliases [:test]})
        cmds (b/java-command
               {:basis basis
                :main 'clojure.main
                :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- pom-template [version]
  [[:description "A simple terminal based data inspector for clojure."]
   [:url "https://github.com/msladecek/inspector"]
   [:licenses
    [:license
     [:name "BSD 3-Clause License"]
     [:url "https://opensource.org/license/BSD-3-clause"]]]
   [:developers
    [:developer
     [:id "msladecek"]
     [:name "Martin Sladecek"]
     [:url "https://msladecek.com"]]]
   [:scm
    [:url "https://github.com/msladecek/inspector"]
    [:connection "scm:git:https://github.com/msladecek/inspector.git"]
    [:developerConnection "scm:git:ssh:git@github.com:msladecek/inspector.git"]
    [:tag (str "v" version)]]])

(defn- jar-opts [opts]
  (let [version-opt (get opts :version version)]
    (assoc opts
           :lib lib
           :version version
           :jar-file (format "target/%s-%s.jar" lib version-opt)
           :basis (b/create-basis {:aliases [:with-optional-deps]})
           :class-dir class-dir
           :target "target"
           :src-dirs ["src"]
           :pom-data (pom-template version-opt))))

(defn build "Cleanup target location and build a fresh JAR" [opts]
  (let [opts (jar-opts opts)]
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println "\nBuilding JAR..." (:jar-file opts))
    (b/jar opts)))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (test opts)
  (b/delete {:path "target"})
  (build opts)
  opts)

(defn install "Install the JAR locally." [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)

(defn publish "Publish the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
