# Inspector

A simple terminal based data inspector for clojure.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/com.msladecek/inspector.svg)](https://clojars.org/com.msladecek/inspector)

(The project is in early development so it may be quite buggy and unstable)

There are multiple ways to run the inspector, the easiest is to make an alias to run nrepl and the inspector together in one terminal window.
See the [Start nrepl and the viewer together](#start-nrepl-and-the-inspector-data-viewer-together) section.

It is possible to run the viewer [alone without the nrepl dependency](#start-the-viewer-alone).
You can then submit data to it over tcp using `send-data!` function which integrates well with [clojure's `tap>`](#submit-data-using-tap) miniframework.

If you wish to run the nrepl integration and the viewer separately, you may [start nrepl with the provided middleware](#submit-data-using-nrepl-middleware).

### Using the viewer

### Start nrepl and the inspector data viewer together

Setup an alias like this:

    ;; deps.edn
    {:aliases {:nrepl+inspector
               {:extra-deps {nrepl/nrepl {:mvn/version "1.4.0"}
                             cider/cider-nrepl {:mvn/version "0.57.0"}
                             com.msladecek/inspector {:mvn/version "< put release version here >"}}
                :main-opts ["-m" "com.msladecek.inspector.nrepl"]}}}

This runs `nrepl.cmdline/-main` and `com.msladecek.inspector/-main` at the same time, in the same terminal window.
The inspector nrepl middleware is automatically included.
For details, run:

    clojure :nrepl+inspector --help

### Start the viewer alone

Run the data viewer in a terminal using the `com.msladecek.inspector/-main` function:

    ;; deps.edn
    {:aliases {:inspector-viewer
               {:extra-deps {com.msladecek/inspector {:mvn/version "< put release version here >"}}
                :main-opts ["-m" "com.msladecek.inspector"]}}}

Then start the viewer:

    clojure -M:inspector-viewer

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

For a full list of viewer options, see the usage help message:

    clojure -M:standalone-inspector --help

### Submit data using `tap>`

    (require '[com.msladecek/inspector :as inspector])
    (add-tap inspector/send-data!)

    (tap> {:hello "world" :message "I submitted this using tap>"})

In the viewer you should see:

    view 1/1
    {:hello "world" :message "I submitted this using tap>"}

### Submit data using nrepl middleware

The inspector can hook into nrepl's `print` function and submits whatever data it receives.

Add the nrepl middleware:

    ;; deps.edn
    {:aliases {:nrepl+inspector-middleware
               {:extra-deps {nrepl/nrepl {:mvn/version "1.4.0"}
                             cider/cider-nrepl {:mvn/version "0.57.0"}
                             com.msladecek/inspector {:git/url "https://github.com/msladecek/inspector-tui"
                                                      :git/sha "<put a commit sha here>"}}
                :main-opts ["-m" "nrepl.cmdline"
                            "--middleware" "[cider.nrepl/cider-middleware com.msladecek.inspector.nrepl/middleware]"]}}}

Start nrepl using the new alias:

    clojure -M:nrepl+inspector-middleware

Connect to the nrepl from your editor and evaluate some expressions, in the viewer you should see the results:

    view 2/2
    {:bonjour "le monde" :message "I evaluated this using nrepl"}

### Traverse the history of submitted data

With <kbd>H</kbd>, and <kbd>L</kbd> keys you can go back and forth through the history of previosly submitted data.
Whenever a new value is submitted to the viewer, it automatically goes to the end of the history and the active view is moved to it.

## Acknowledgements

Inspired by the inspector tool that ships with [`cider`](https://cider.mx/) (emacs plugin).
I wanted a basic, editor-agnostic tool which replicates some of its functionality.

## Alternatives

Other (more featureful) data viewer tools exist:

- [`clojure.inspector`](https://clojure.github.io/clojure/clojure.inspector-api.html) graphical inspector distributed along with clojure
- [cider inspector](https://docs.cider.mx/cider/debugging/inspector.html) (emacs plugin)
- [vlaaad/reveal](https://vlaaad.github.io/reveal/)
- [djblue/portal](https://github.com/djblue/portal)

Feel free to open an issue or submit a pull request if you're aware of others.
