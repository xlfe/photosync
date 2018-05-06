(ns photosync.core
  (:require [goog.events :as events]
            [goog.dom :as gdom]
            [cljsjs.material-ui]                            ; I recommend adding this at the beginning of core file
    ;  so React is always loaded first. It's not always needed
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]           ; SVG icons that comes with MaterialUI
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [compassus.core :as compassus]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [goog.events :as evt]

            [photosync.util :as util :refer [hidden pluralize]]
            [photosync.jobs :as jobs]
            [photosync.parser :as p])
  (:import [goog History]
           [goog.history EventType]))

;; -----------------------------------------------------------------------------
;; Components

(defn set-hash! [loc]
  (set! (.-hash js/window.location) loc))
(defn redirect! [loc]
  (set! (.-location js/window) loc))


(enable-console-print!)

(defn appbar [this title]
  (ui/app-bar {
               :title title
               :iconElementLeft (ui/icon-button {
                                                 :className "menubutton"
                                                 :onClick (fn [e] (om/transact! this '[(open-app-bar)]))}
                                 (ic/navigation-menu))}))


(defui ^:once Base
  static om/IQuery
  (query [this]
    '[:drawer :user])
  Object
  (render [this]
    (let [{:keys [owner factory props]} (om/get-computed this)
          {:keys [drawer user]} (om/props this)]
      (ui/mui-theme-provider {
                              :mui-theme (ui/get-mui-theme {:palette {:primary1-color (ui/color :deep-purple-500)}})
                              :children [
                                         (factory props)
                                         (appbar this (str (:given_name user) "'s Sync Jobs"))
                                         (ui/drawer {
                                                     :onRequestChange (fn [_] (om/transact! this '[(close-app-bar)]))

                                                     :docked false :open (= true drawer)}
                                           (ui/menu-item {:key 0} "PhotoSync")
                                           (ui/divider)
                                           (ui/menu-item {:key 1 :leftIcon (ic/notification-sync)} "Sync Jobs")
                                           (ui/menu-item {:key 2 :leftIcon (ic/content-link)} "Linked Services")
                                           (ui/menu-item {:key 3 :leftIcon (ic/action-credit-card)} "Billing"))]}))))


(defui ^:once Jobs
  static om/IQuery
  (query [this]
    '[:jobs/list ?job])
  static om/IQueryParams
  (params [this]
    {:job (om/get-query jobs/JobItem)})
  Object
  (render [this]
    (let [props (om/props this)
          {:keys [jobs/list user]} props]
      (dom/div nil


               (ui/floating-action-button
                 {
                  :style    #js {
                                 :margin   "10px"
                                 :position "absolute"
                                 :bottom   "10px"
                                 :right    "10px"}}
                  ;:on-click #("blah")}
                 (ic/content-add))))))




(defui ^:once Login
  Object
  (render [this]
    (ui/dialog
      {
       :actions
              [
               (ui/flat-button {:label "Cancel" :primary false :onClick #(redirect! "https://google.com")})
               (ui/flat-button {:label "Login" :primary true :onClick #(redirect! "/login")})]
       :open  true :modal true
       :title "Please login to PhotoSync.Net using your Google Account"}
      "PhotoSync.Net uses your Google Account to identify you")))




(defonce reconciler
  (om/reconciler
    {:state     (atom {:drawer false})
     :normalize true
     :parser    (compassus/parser {:read p/read :mutate p/mutate :route-dispatch false})
     :send      (util/edn-post "/api")}))


(declare app)

(defroute welcome "/" []
          (compassus/set-route! app :index))

(defroute login "/login" []
          (compassus/set-route! app :login))

(def event-key (atom nil))
(def history
  (History.))


(def app
  (compassus/application
    {:routes  {
               :login Login
               :index Jobs}

     :index-route :index
     :reconciler reconciler
     :mixins [
              (compassus/wrap-render Base)
              (compassus/did-mount (fn [_]
                                     (reset! event-key
                                             (evt/listen history EventType/NAVIGATE
                                                         #(secretary/dispatch! (.-token %))))
                                     (.setEnabled history true)))
              (compassus/will-unmount (fn [_]
                                        (evt/unlistenByKey @event-key)))]}))

(compassus/mount! app (js/document.getElementById "app"))
