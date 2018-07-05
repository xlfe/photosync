(def appengine-version "1.9.63")

(defproject photosync "0.0.1-SNAPSHOT"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.omcljs/om "1.0.0-beta4" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [cljs-react-material-ui "0.2.50"]
                 [cljsjs/react "16.2.0-3"]
                 [cljsjs/react-dom "16.2.0-3"]
                 [camel-snake-kebab "0.4.0"]
                 [clj-oauth "1.5.5"]
                 [com.taoensso/nippy "2.14.0"]
                 [clojure-humanize "0.2.2"]

                 [ring "1.6.3"]
                 [ring/ring-ssl "0.3.0"]
                 [clojure.java-time "0.3.2"]

                 [clj-http "3.7.0"]

                 [cheshire "5.8.0"]
                 [secretary "1.2.3"]
                 [compassus "1.0.0-alpha3"]
                 [hyperion/hyperion-gae "3.7.2-SNAPSHOT"]

                 [com.google.appengine/appengine-api-1.0-sdk ~appengine-version]
                 [com.google.api-client/google-api-client "1.23.0"]
                 [liberator "0.15.1"]
                 [compojure "1.6.0"]]


  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-ring "0.12.1" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.14"]]


  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} ["resources/public/js"
                                    :target-path]

  :ring {
         :web-xml "web.xml"
         :handler photosync.core/prod-handler}

  :profiles {
             :dev
             {
              :repl-options {
                             :init-ns photosync.appengine
                             :init    (start-it)}

              :source-paths ["dev/clj"]
              :dependencies
                            [[com.google.appengine/appengine-api-stubs ~appengine-version]
                             [com.google.appengine/appengine-local-runtime ~appengine-version]
                             [com.google.appengine/appengine-local-runtime-shared ~appengine-version]]}}

  :cljsbuild {
              :builds {
                       :dev        {
                                    :source-paths ["src/cljs" "src/clj" "dev/cljs"]
                                    :figwheel     true
                                    :compiler     {:output-to     "resources/public/js/main.js"
                                                   :output-dir    "resources/public/js/out"
                                                   ;:main          photosync.core
                                                   :main          photosync.dev
                                                   :asset-path    "js/out"
                                                   :infer-externs true
                                                   :optimizations :none
                                                   :source-map    true}}

                       :production {
                                    :source-paths ["src/cljs"]
                                    ;:figwheel     false
                                    :compiler     {:output-to     "resources/public/js/main.js"
                                                   :infer-externs true
                                                   ;:pseudo-names true,
                                                   ;:pretty-print true
                                                   :optimizations :advanced}}}})









