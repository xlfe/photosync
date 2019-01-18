(ns photosync.core
  (:require [goog.events :as events]
            [goog.dom :as gdom]
            [cljs-material-ui.core :as ui]
            [cljs-material-ui.icons :as ic]           ; SVG icons that comes with MaterialUI
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [compassus.core :as compassus]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [goog.events :as evt]

            [photosync.util :as util :refer [hidden pluralize]]
            [photosync.jobs :as jobs]
            [photosync.services :as services]
            [photosync.parser :as p])
  (:import [goog History]
           [goog.history Html5History EventType]))

;; -----------------------------------------------------------------------------
;; Components

(enable-console-print!)

(def log (.-log js/console))
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
               :position "static"}
              (ui/toolbar {}
                (ui/icon-button {:onClick (fn [_] (om/update-state! this assoc :drawer true))}
                                (ic/menu))
                (ui/typography {:variant "h6"} title))))
               ;:iconElementLeft (ui/icon-button
               ;                  (ic/navigation-menu)}))

(defn do-nav
  [route]
  (.setToken history (name route))
  (set! (.-title js/document) ((route route-titles) nil)))


(defn nav-button [destination text] (ui/button {:onClick (fn [_] (do-nav destination))} text))

(defn menu-redirect
  ([icon text uri]
   (menu-redirect icon text uri false))
  ([icon text uri disabled]
   (ui/menu-item {:onClick #(util/redirect! uri) :disabled (= true disabled)}
                 (ui/list-item-icon icon)
                 (ui/list-item-text text))))


(defn menu-click [this icon text route]
 (ui/menu-item {
                :onClick (fn [_]
                           (do-nav route)
                           (om/update-state! this assoc :drawer false))}
               (ui/list-item-icon icon)
               (ui/list-item-text text)))



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
                              :theme (ui/get-mui-theme
                                       {
                                        :typography {:use-next-variants true}
                                        :palette
                                        {:primary
                                         {:main
                                          (ui/color :deep-orange :500)}}})}
                             (dom/div nil
                                      (appbar this ((or ((compassus/current-route this) route-titles) (fn [_] (str "PhotoSync"))) user))
                                      (ui/drawer {
                                                  :onClose (fn [_] (om/update-state! this assoc :drawer false))
                                                  ;:docked          false
                                                  :open (= true drawer)}
                                                 (menu-click this (ic/blur-circular) "PhotoSync" :welcome)
                                                 (ui/divider)
                                                 (menu-click this (ic/sync) "Sync Jobs" :jobs)
                                                 (menu-click this (ic/link) "Linked Services" :services)
                                                 (menu-click this (ic/credit-card) "Billing" :billing))
                                      (dom/div #js {:style {:padding "100px"}}
                                        (factory props)))))))

(def fab-props
  {
   :variant "fab"
   :color "primary"
   :style #js {:position "absolute"  :left "1em" :bottom   "1em"}})

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
             (ui/button
               (merge fab-props
                      {:on-click #("blah")})
               (ic/add)))))

(defui ^:once Services
  static om/IQuery
  (query [this]
   [{:services/list (om/get-query services/Service)}])
   ;'[:services/list])
  Object
  (render [this]
    (let [
          {:keys [menu-shown]} (om/get-state this)
          {:keys [services/list]} (om/props this)
          has-smugmug (not (empty? (filter #(= (:source %) "smugmug") list)))]
      (dom/div #js {:className "col-lg-6 col-sm-12"}
         (map services/service list)
         (ui/button (merge fab-props
                      {
                       :id "fab-services"
                       :onClick (fn [_] (om/update-state! this assoc :menu-shown true))})
                 (ic/add))
         (ui/popover {
                      :open (= menu-shown true)
                      :anchorEl (js/document.getElementById "fab-services")
                      :anchorReference "anchorEl"
                      :onClose (fn [_] (om/update-state! this assoc :menu-shown false))
                      :anchorOrigin {:horizontal "right" :vertical "top"}}
          (ui/menu-list
            {:open menu-shown}
            (menu-redirect (ic/photo-album) "SmugMug" "/getsmug" has-smugmug)))))))

(defui ^:once Jobs
  ;static om/IQuery
  ;(query [this]
  ;  '[:jobs/list ?job]
  ;static om/IQueryParams
  ;(params [this]
  ;  {:job (om/get-query jobs/JobItem)}
  Object
  (render [this]
    (let [props (om/props this)
          {:keys [jobs/list user]} props]
      (dom/div nil
               (ui/button (merge fab-props
                                 {:id "fab-services"
                                  :onClick (fn [_] (om/update-state! this assoc :menu-shown true))})
                          (ic/add))))))



(def yashica
 "https://lh3.googleusercontent.com/qx4Ni3XPOpQq0sp5MIB_9R3YTbTD8509l_f851Em7XzeXAYcV7NqhlwB5u8VFlFwgJLeuw94qZDZ6J51GDVg0YY=s1600")




(defui ^:once Welcome
  Object
  (render [this]
      (dom/div #js {:className "col-lg-6 col-sm-12"}
            (ui/card
              (ui/card-header {:title "Welcome to PhotoSync"})
              (ui/card-media
                {:title "Relax"
                 :image yashica
                 :subtitle "We're here to help"})
               ;(dom/img #js {:src yashica}))
              (ui/card-content)
                ;(dom/h6 {:title "Let's get started" :subtitle "You'll be up and running in no time"})
                ;(ui/card-text nil "A few easy steps")

              (ui/card-actions
                (nav-button :services "1. Link a service")
                (nav-button :billing "2. Setup your billing details")
                (nav-button :jobs "3. Create a sync job"))))))




(defui ^:once Login
  Object
  (render [this]
    (ui/dialog
      {
       :actions
              [
               (ui/button {:label "Cancel" :primary false :onClick #(util/redirect! "https://google.com")})
               (ui/button {:label "Login" :primary true :onClick #(util/redirect! "/login")})]
       :open  true :modal true
       :title "Session Expired"}
      "Your session has expired - please login again.")))




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
