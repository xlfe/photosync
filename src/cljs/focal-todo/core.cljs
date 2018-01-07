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

(defn main [todos {:keys [todos/list] :as props}]
  (let [checked? (every? :todo/completed list)]
    (dom/section #js {:id "main" :style (hidden (empty? list))}
     (ui/checkbox
       {
        :id       "toggle-all"
        :onCheck (fn [_]
                    (om/transact! todos
                          `[(todos/toggle-all
                              {:value ~(not checked?)})
                            :todos/list]))
        :checked  checked?})
     (ui/list
       {:id "todo-list"}
       (map item/item list)))))

(defn clear-button [todos completed]
  (when (pos? completed)
    (dom/button
      #js {:id "clear-completed"
           :onClick (fn [_] (om/transact! todos `[(todos/clear)]))}
      (str "Clear completed (" completed ")"))))

(defn footer [todos props active completed]
  (dom/footer #js {:id "footer" :style (hidden (empty? (:todos/list props)))}
    (dom/span #js {:id "todo-count"}
      (dom/strong nil active)
      (str " " (pluralize active "item") " left"))
    (apply dom/ul #js {:id "filters" :className (name (:todos/showing props))}
      (map (fn [[x y]] (dom/li nil (dom/a #js {:href (str "#/" x)} y)))
        [["" "All"] ["active" "Active"] ["completed" "Completed"]]))
    (clear-button todos completed)))

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
          completed (- (count list) active)]
      (ui/mui-theme-provider
        {:mui-theme (ui/get-mui-theme
                      {:palette                             ; You can use either camelCase or kebab-case
                       {:primary1-color (ui/color :deep-purple-500)}
                       :raised-button
                       {:primary-text-color (ui/color :light-black)
                        :font-weight        200}})}
        (dom/div nil
          (ui/app-bar {:title "Todos App"})
          (dom/header #js {:id "header"}
            (dom/h1 nil "todos")
            (ui/text-field
              {:ref "newField"
               :id "new-todo"
               :placeholder "What needs to be done?"})
               ;:onKeyDown #(do %)})
            (main this props)
            (footer this props active completed)))))))

(def todos (om/factory Todos))

(defonce reconciler
  (om/reconciler
    {:state     (atom {})
     :normalize true
     :parser    (om/parser {:read p/read :mutate p/mutate})
     :send      (util/edn-post "/api")}))

;(om/add-root! reconciler Todos (gdom/getElement "todoapp"))

(defonce root (atom nil))

(defn init []
  (if (nil? @root)
    (let [target (js/document.getElementById "todoapp")]
      (om/add-root! reconciler Todos target)
      (reset! root Todos))
    (.forceUpdate (om/class->any reconciler Todos))))

(init)
