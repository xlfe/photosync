(ns om-async.core
  (:require [cljs.reader :as reader]
            [figwheel.client :as fw]
            [goog.events :as events]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType]))

(enable-console-print!)

(println "Hello world!")

(fw/start {:websocket-url "ws://localhost:3449/figwheel-ws"})
