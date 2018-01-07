(def appengine-version "1.9.58")

(defproject focal-woods "0.1.0-SNAPSHOT"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.omcljs/om "1.0.0-beta2-SNAPSHOT" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [cljs-react-material-ui "0.2.50"]
                 [cljsjs/react "16.2.0-3"]
                 [cljsjs/react-dom "16.2.0-3"]
                 [camel-snake-kebab "0.4.0"]

                 [ring "1.6.3"]

                 [com.google.appengine/appengine-api-1.0-sdk ~appengine-version]
                 [liberator "0.15.1"]
                 [compojure "1.6.0"]]


  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-ring "0.12.1" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.14"]]


  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} ["resources/public/js/out"
                                    "resources/public/js/main.js"]

  :ring {
         :handler cae.core/prod-handler}

  :profiles {
             :dev
             {
              :repl-options {
                             :init-ns cae.appengine
                             :init    (start-it)}

              :source-paths ["dev/"]
              :dependencies
                            [[com.google.appengine/appengine-api-stubs ~appengine-version]
                             [com.google.appengine/appengine-local-runtime ~appengine-version]
                             [com.google.appengine/appengine-local-runtime-shared ~appengine-version]]}}

  :cljsbuild {
              :builds [
                       {
                        :id           "dev"
                        :source-paths ["src/cljs" "src/clj"]
                        :figwheel     true
                        :compiler     {:output-to     "resources/public/js/main.js"
                                       :output-dir    "resources/public/js/out"
                                       :main          focal-todo.core
                                       :asset-path    "js/out"
                                       :optimizations :none
                                       :source-map    true}}


                       {
                        :id           "production"
                        :source-paths ["src/cljs"]
                        :figwheel     false
                        :compiler     {:output-to     "resources/public/js/main.js"
                                       :output-dir    "resources/public/js/"
                                       :asset-path    "js"
                                       :main          focal-todo.core
                                       :optimizations :advanced}}]})





