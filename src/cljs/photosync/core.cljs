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
           [goog.history Html5History EventType]))

;; -----------------------------------------------------------------------------
;; Components

(defn set-hash! [loc]
  (set! (.-hash js/window.location) loc))
(defn redirect! [loc]
  (set! (.-location js/window) loc))


(enable-console-print!)


(declare app)
(def ^:once event-key (atom nil))
(def ^:once history (Html5History.))

(defroute welcome "/welcome" [] (compassus/set-route! app :welcome))
(defroute jobs "/jobs" [] (compassus/set-route! app :jobs))
(defroute services "/services" [] (compassus/set-route! app :services))
(defroute billing "/billing" [] (compassus/set-route! app :billing))

(def route-titles
  {
   :welcome (fn [user] (str (:given_name user) "'s PhotoSync"))
   :jobs (fn [user] (str (:given_name user) "'s Sync Jobs"))
   :billing (fn [user] (str (:given_name user) "'s Billing Details"))
   :services (fn [user] (str (:given_name user) "'s Linked Accounts"))})




(defn appbar [this title]
  (ui/app-bar {
               :title title
               :iconElementLeft (ui/icon-button {
                                                 :className "menubutton"
                                                 :onClick (fn [e] (om/transact! this '[(open-app-bar)]))}
                                 (ic/navigation-menu))}))

(defn menu-click [this key icon text route]
 (ui/menu-item {
                ;:onClick (fn [_] (compassus.core/set-route! this route))
                :onClick (fn [_]
                           ;(compassus.core/set-route! app route {:tx '[(close-app-bar)]})
                           (.setToken history (name route))
                           (om/transact! this '[(close-app-bar)]))
                :key key
                :leftIcon icon} text))

(defui ^:once Base
  static om/IQuery
  (query [this]
    '[:drawer :user])
  Object
  (render [this]
    (let [{:keys [owner factory props]} (om/get-computed this)
          {:keys [drawer user]} (om/props this)]
      (ui/mui-theme-provider {
                              :mui-theme (ui/get-mui-theme {:palette {:primary1-color (ui/color :deep-purple-500)}})}
                         (dom/div nil
                             (println (compassus/current-route this))
                             (appbar this (((compassus/current-route this) route-titles) user))
                             (ui/drawer {
                                         :onRequestChange (fn [_] (om/transact! this '[(close-app-bar)]))

                                         :docked false :open (= true drawer)}
                               (menu-click this 0 nil "PhotoSync" :welcome)
                               (ui/divider)
                               (menu-click this 1 (ic/notification-sync) "Sync Jobs" :jobs)
                               (menu-click this 2 (ic/content-link) "Linked Services" :services)
                               (menu-click this 3 (ic/action-credit-card) "Billing" :billing))
                             (factory props))))))


(defui ^:once Billing
  ;static om/IQuery
  ;(query [this]
  ;  '[:jobs/list ?job]
  ;static om/IQueryParams
  ;(params [this]
  ;  {:job (om/get-query jobs/JobItem)}
  Object
  (render [this]
    ;(let [props (om/props this)
    ;      {:keys [jobs/list user]} props
    (dom/div nil


             (ui/floating-action-button
               {
                :style    #js {
                               :margin   "10px"
                               :position "absolute"
                               :bottom   "10px"
                               :right    "10px"}}
               ;:on-click #("blah")}
               (ic/content-add)))))


(defui ^:once Services
  ;static om/IQuery
  ;(query [this]
  ;  '[:jobs/list ?job]
  ;static om/IQueryParams
  ;(params [this]
  ;  {:job (om/get-query jobs/JobItem)}
  Object
  (render [this]
    ;(let [props (om/props this)
    ;      {:keys [jobs/list user]} props
      (dom/div nil


               (ui/floating-action-button
                 {
                  :style    #js {
                                 :margin   "10px"
                                 :position "absolute"
                                 :bottom   "10px"
                                 :right    "10px"}}
                 ;:on-click #("blah")}
                 (ic/content-add)))))

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


(defui ^:once Welcome
  Object
  (render [this]
      (dom/div nil
               "Welcome!")))



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




(def app
  (compassus/application
    {:routes  {
               :billing Billing
               :services Services
               :welcome Welcome
               :jobs Jobs}

     :index-route :welcome
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
