# Inspector TUI

A simple terminal based data inspector for clojure.

## Usage

(The project is in early development so it may be quite buggy and unstable)

### Start the viewer

Run the data viewer in a terminal using the `start-viewer` command, it may be useful to create an `:inspector` alias first.

    ;; deps.edn
    {:aliases {:inspector
               {:extra-deps {com.msladecek/inspector {:git/url "https://github.com/msladecek/inspector-tui"
                                                      :git/sha "<put a commit sha here>"}}
                :main-opts ["-m" "com.msladecek.inspector"]}}}

Then start the viewer:

    clojure -M:inspector start-viewer

Once the viewer starts, your terminal will look like this:

    view -/0
    no view selected

The basic viewer will display lists of maps using `clojure.pprint/print-table` and everything else using `clojure.pprint/pprint`, for example:

    [:one 2 "three"]

shows up as:

    view 1/1
    [:one 2 "three"]

whereas

    [{:id :one :color "red"}
     {:id 2 :color "blue" :flavor "sour"}
     {:id "three" :flavor "sweet"}]

shows up as:

    view 2/2

    | :color | :flavor |   :id |
    |--------+---------+-------|
    |    red |         |  :one |
    |   blue |    sour |     2 |
    |        |   sweet | three |

### Submit data using `tap>`

    (require '[com.msladecek/inspector :as inspector])
    (add-tap inspector/send-data!)

    (tap> {:hello "world" :message "I submitted this using tap>"})

In the viewer you should see:

    view 1/1
    {:hello "world" :message "I submitted this using tap>"}

### Submit data using nrepl middleware

The inspector can hooks into nrepl's `print` function and submits whatever data it receives.

Add the nrepl middleware:

    ;; deps.edn
    {:aliases :nrepl+inspector
              {:extra-deps {nrepl/nrepl {:mvn/version "1.4.0"}
                            cider/cider-nrepl {:mvn/version "0.57.0"}
                            com.msladecek/inspector {:git/url "https://github.com/msladecek/inspector-tui"
                                                     :git/sha "<put a commit sha here>"}}
               :main-opts ["-m" "nrepl.cmdline"
                           "--middleware" "[cider.nrepl/cider-middleware com.msladecek.inspector.nrepl/middleware]"]}}

Start nrepl using the new alias:

    clojure -M:nrepl+inspector

Connect to the nrepl from your editor and evaluate some expressions, in the viewer you should see the results:

    view 2/2
    {:bonjour "le monde" :message "I evaluated this using nrepl"}

### Traverse the history of traversed data

With <kbd>H</kbd>, and <kbd>L</kbd> keys you can go back and forth through the history of previosly submitted data.
Whenever a new value is submitted to the viewer, it automatically goes to the end of the history and the active view is moved to it.

## Acknowledgements

Inspired by the inspector tool that ships with [`cider`](https://cider.mx/) (emacs plugin).
I wanted a basic, editor agnostic tool which replicates some of its functionality.

## Alternatives

Other (more featureful) data viewer tools exist:

- [cider inspector](https://docs.cider.mx/cider/debugging/inspector.html) (emacs plugin)
- [vlaaad/reveal](https://vlaaad.github.io/reveal/)
- [djblue/portal](https://github.com/djblue/portal)

Feel free to open an issue or submit a pull request if you're aware of others.
