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



(declare app)
(def ^:once event-key (atom nil))
(def ^:once history (Html5History.))

(defroute index "/" [] (compassus/set-route! app :welcome))
(defroute welcome "/welcome" [] (compassus/set-route! app :welcome))
(defroute jobs "/jobs" [] (compassus/set-route! app :jobs))
(defroute services "/services" [] (compassus/set-route! app :services))
(defroute billing "/billing" [] (compassus/set-route! app :billing))


(defn no-user
 [text]
 (fn [user]
   (if user
    (str (:given_name user) "'s " text)
    text)))

(def route-titles
  {
   :welcome (no-user "PhotoSync")
   :jobs (no-user "Sync Jobs")
   :billing (no-user "Billing Details")
   :services (no-user "Linked Accounts")})




(defn appbar [this title]
  (ui/app-bar {
               :title title
               :iconElementLeft (ui/icon-button {
                                                 :className "menubutton"
                                                 :onClick (fn [_] (om/update-state! this assoc :drawer true))}
                                 (ic/navigation-menu))}))

(defn do-nav
  [route]
  (.setToken history (name route))
  (set! (.-title js/document) ((route route-titles) nil)))



(defn menu-click [this key icon text route]
 (ui/menu-item {
                :onClick (fn [_]
                           (do-nav route)
                           (om/update-state! this assoc :drawer false))
                :key key
                :leftIcon icon} text))

(defui ^:once Base
  static om/IQuery
  (query [this]
    '[:user])
  Object
  (render [this]
    (let [{:keys [_ factory props]} (om/get-computed this)
          {:keys [user]} (om/props this)
          {:keys [drawer]} (om/get-state this)]
      (ui/mui-theme-provider {
                              :mui-theme (ui/get-mui-theme {:palette {:primary1-color (ui/color :deep-orange-500)}})}
                             (dom/div nil
                                      (appbar this ((or ((compassus/current-route this) route-titles) (fn [_] (str "PhotoSync"))) user))
                                      (ui/drawer {
                                                  :onRequestChange (fn [_] (om/update-state! this assoc :drawer (false? drawer)))
                                                  :docked          false :open (= true drawer)}
                                                 (menu-click this 0 nil "PhotoSync" :welcome)
                                                 (ui/divider)
                                                 (menu-click this 1 (ic/notification-sync) "Sync Jobs" :jobs)
                                                 (menu-click this 2 (ic/content-link) "Linked Services" :services)
                                                 (menu-click this 3 (ic/action-credit-card) "Billing" :billing))
                                      (dom/div #js {:style {:padding "100px"}}
                                        (factory props)))))))


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
  static om/IQuery
  (query [this]
    '[:services/list ?service])
  static om/IQueryParams
  (params [this]
    {:service (om/get-query jobs/JobItem)})
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


(def yashica
 "https://lh3.googleusercontent.com/qx4Ni3XPOpQq0sp5MIB_9R3YTbTD8509l_f851Em7XzeXAYcV7NqhlwB5u8VFlFwgJLeuw94qZDZ6J51GDVg0YY=s1600")


(defui ^:once Welcome
  Object
  (render [this]
      (dom/div #js {:className "col-lg-6 col-sm-12"}
            (ui/card
              (ui/card-header {:title "Welcome to PhotoSync"})
              (ui/card-media {:overlay (ui/card-title {:title "Relax" :subtitle "We're here to help"})}
                (dom/img #js {:src yashica}))
              (ui/card-title {:title "Let's get started" :subtitle "You'll be up and running in no time"})
              (ui/card-text nil "A few easy steps")
              (ui/card-actions nil
                 (ui/flat-button {:onClick (fn [_] (do-nav :services)) :label "1. Link a service"})
                 (ui/flat-button {:onClick (fn [_] (do-nav :billing)) :label "2. Setup your billing details"})
                 (ui/flat-button {:onClick (fn [_] (do-nav :jobs)) :label "3. Create a sync job"}))))))




(defui ^:once Login
  Object
  (render [this]
    (ui/dialog
      {
       :actions
              [
               (ui/flat-button {:label "Cancel" :primary false :onClick #(util/redirect! "https://google.com")})
               (ui/flat-button {:label "Login" :primary true :onClick #(util/redirect! "/login")})]
       :open  true :modal true
       :title "Session Expired"}
      "Your session has expired - please login again.")))




(defonce reconciler
  (om/reconciler
    {:state     (atom {:drawer false})
     :normalize true
     :parser    (compassus/parser {:read p/read :mutate p/mutate :route-dispatch false})
     :send      (util/edn-post "/api")}))


(def log (.-log js/console))

(def app
  (compassus/application
    {:routes  {
               :billing Billing
               :services Services
               :welcome Welcome
               :login Login
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
