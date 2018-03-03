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
            [photosync.item :as item]
            [photosync.auth :as auth]
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

(defn main [todos {:keys [todos/list]}])

(defn clear-button [todos completed]
  (when (pos? completed)
    (dom/button
      #js {:id "clear-completed"
           :onClick (fn [_] (om/transact! todos `[(todos/clear)]))}
      (str "Clear completed (" completed ")"))))

(defn footer
  [todos props active completed]
  (dom/div #js {:className "bottom row"}
   (dom/div #js {:className "col-lg-12"}
    (ui/paper {:zdepth 1}
      (ui/bottom-navigation nil
       (ui/bottom-navigation-item {
                                   :icon (ic/content-select-all) :label "All"
                                   :onClick  (fn [_] (om/transact! todos `[(todos/clear)]))})

       (ui/bottom-navigation-item {:icon (ic/action-update) :label "Active"})
       (ui/bottom-navigation-item {:icon (ic/action-done-all) :label "Completed"}))))))

(defui ^:once Base
  Object
  (render [this]
    (let [{:keys [owner factory props]} (om/get-computed this)
          route (compassus/current-route this)]
      (ui/mui-theme-provider {:mui-theme (ui/get-mui-theme {:palette {:primary1-color (ui/color :deep-purple-500)}})}
       (dom/div []
        (ui/app-bar {:title "PhotoSync .Net"})
        (factory props))))))


(defui ^:once Todos
  static om/IQueryParams
  (params [this]
    {:todo-item (om/get-query item/TodoItem)})

  static om/IQuery
  (query [this]
    '[{:todos/list ?todo-item}])

  Object
  (render [this]
    (let [props (merge (om/props this) {:todos/showing :all})
          {:keys [todos/list]} props
          active (count (remove :todo/completed list))
          checked? (every? :todo/completed list)
          completed (- (count list) active)]

     (dom/div #js {:className "col-lg-offset-3 col-lg-6"}
      (ui/paper
       (ui/list
        (ui/subheader "New todo -")
        (ui/list-item {
                              :primaryText (ui/text-field
                                                  {:ref "newField"
                                                   :id "new-todo"
                                                   :placeholder "What needs to be done?"
                                                   :onKeyDown (fn [e] (item/key-down this props e))})})
        (ui/divider {:inset true})
        (ui/subheader "Existing todos")
        (map item/item list))
       (ui/divider {:inset true})
       (footer this props active completed))))))


(defui ^:once Login
  Object
  (render [this]
   (dom/div #js {:className "col-lg-offset-3 col-lg-6"}
    (ui/dialog
      {
       :actions
       [
          (ui/flat-button {:label "Cancel" :primary false :onClick #(redirect! "https://google.com")})
          (ui/flat-button {:label "Login" :primary true :onClick #(auth/sign-in)})]
          ;(ui/flat-button {:label "Login" :primary true :onClick #(redirect! "/gauth")})]

       :title "Please login using your Google Account"
       :open true :modal true}
      "If you haven't logged in before we will
       ask for access to Google Photos as well"))))


(defui ^:once Welcome
  Object
  (render [this]
    (let [props (merge (om/props this) {:todos/showing :all})
          {:keys [todos/list]} props
          active (count (remove :todo/completed list))
          checked? (every? :todo/completed list)
          completed (- (count list) active)]

      (dom/div #js {:className "col-lg-offset-3 col-lg-6"}
       (ui/dialog {
                   :actions [
                             (ui/flat-button {:label "Cancel" :primary false :onClick #(redirect! "https://google.com")})
                             (ui/flat-button {:label "Allow" :primary true :onClick #(redirect! "/login")})]

                   :title "Allow PhotoSync to access your Google Photos?"
                   :open true :modal true}
         "PhotoSync requires access to your Google Photos account in order to upload photos")))))




;(def todos (om/factory Todos))

(defonce reconciler
  (om/reconciler
    {:state     (atom {})
     :normalize true
     :parser    (compassus/parser {:read p/read :mutate p/mutate :route-dispatch false})
     :send      (util/edn-post "/api")}))


(declare app)

(defroute index "/" []
          (compassus/set-route! app :index))

(defroute login "/login" []
          (compassus/set-route! app :login))


(defroute welcome "/welcome" []
          (compassus/set-route! app :welcome))


(def event-key (atom nil))
(def history
  (History.))



(def app
  (compassus/application
    {:routes  {
               :index Todos
               :login Login
               :welcome Welcome}

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

;(om/add-root! reconciler Todos (js/document.getElementById "app"))

(defn do-auth []
  (compassus/mount! app (js/document.getElementById "app"))
  (auth/google-auth-init app))

(auth/google-auth-load do-auth)
