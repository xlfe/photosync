(ns focal-todo.core
  (:require [goog.events :as events]
            [goog.dom :as gdom]
            [cljsjs.material-ui]                            ; I recommend adding this at the beginning of core file
    ;  so React is always loaded first. It's not always needed
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]           ; SVG icons that comes with MaterialUI
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [focal-todo.util :as util :refer [hidden pluralize]]
            [focal-todo.item :as item]
            [focal-todo.parser :as p])
  (:import [goog History]
           [goog.history EventType]))

;; -----------------------------------------------------------------------------
;; Components

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
      (ui/mui-theme-provider
        {:mui-theme (ui/get-mui-theme
                      {:palette                             ; You can use either camelCase or kebab-case
                       {:primary1-color (ui/color :deep-purple-500)}})}
                      ; :raised-button
                      ; {:primary-text-color (ui/color :light-black)})}
        (dom/div []
         (ui/app-bar {
                      :iconElementLeft (ui/icon-button
                                         {
                                          ;:id "toggle-all"
                                          ;:disabled false
                                          :onClick (fn [_]
                                                      (om/transact! this
                                                       `[(todos/toggle-all
                                                           {:value ~(not checked?)})
                                                         :todos/list]))}
                                         (ic/action-done))
                      :title "Syncro A"})

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
            (footer this props active completed))))))))


;(def todos (om/factory Todos))

(defonce reconciler
  (om/reconciler
    {:state     (atom {})
     :normalize true
     :parser    (om/parser {:read p/read :mutate p/mutate})
     :send      (util/edn-post "/api")}))

(om/add-root! reconciler Todos (js/document.getElementById "app"))
;(defonce root (atom nil))
;(defn init []
;  (if (nil? @root)
;    (let [target (js/document.getElementById "app")]
;      (om/add-root! reconciler Todos target)
;      (reset! root Todos)
;    (.forceUpdate (om/class->any reconciler Todos)))
;(init)
