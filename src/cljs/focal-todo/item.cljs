(ns focal-todo.item
  (:require [clojure.string :as string]
            [cljsjs.material-ui]                            ; I recommend adding this at the beginning of core file
    ;  so React is always loaded first. It's not always needed
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]           ; SVG icons that comes with MaterialUI
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [focal-todo.util :refer [hidden pluralize]]))

(def ESCAPE_KEY 27)
(def ENTER_KEY 13)

(defn submit [c {:keys [db/id todo/title] :as props} e]
  (let [edit-text (string/trim (or (om/get-state c :edit-text) ""))]
    (when-not (= edit-text title)
      (om/transact! c
        (cond-> '[(todo/cancel-edit)]
          (= :temp id)
          (conj '(todos/delete-temp))

          (and (not (string/blank? edit-text))
            (not= edit-text title))
          (into
            `[(todo/update {:db/id ~id :todo/title ~edit-text})
              '[:todos/by-id ~id]]))))
    (doto e (.preventDefault) (.stopPropagation))))

(defn edit [c {:keys [db/id todo/title] :as props}]
  (om/transact! c `[(todo/edit {:db/id ~id})])
  (om/update-state! c merge {:needs-focus true :edit-text title}))

(defn key-down [c {:keys [todo/title] :as props} e]
  (condp == (.-keyCode e)
    ESCAPE_KEY
      (do
        (om/transact! c '[(todo/cancel-edit)])
        (om/update-state! c assoc :edit-text title)
        (doto e (.preventDefault) (.stopPropagation)))
    ENTER_KEY
      (submit c props e)
    nil))

(defn change [c e]
  (om/update-state! c assoc
    :edit-text (.. e -target -value)))

;; -----------------------------------------------------------------------------
;; Todo Item

;(defn label [c {:keys [todo/title] :as props}]
;  title
  ;(ui/text-field
  ;  {
  ;   :value title)
  ;   :onDoubleClick (fn [e] (edit c props))))
;
(defn checkbox [c {:keys [:db/id :todo/title :todo/completed]}]
  (ui/checkbox
    {
     :checked completed
     :onCheck (fn [_]
                (om/transact!
                  c
                  `[(todo/update {:db/id ~id :todo/completed ~(not completed)}) '[:todos/by-id ~id]])
                (doto _ (.preventDefault) (.stopPropagation)))}))



(defn delete-button [c {:keys [db/id]}]
  (dom/button
    #js {
         :onClick (fn [_] (om/transact! c `[(todo/delete {:db/id ~id})]))}))

(defn edit-field [c props]
  (dom/input
    #js {:ref       "editField"
         :className "edit"
         :value     (or (om/get-state c :edit-text) "")
         :onBlur    #(submit c props %)
         :onChange  #(change c %)
         :onKeyDown #(key-down c props %)}))

(defui ^:once TodoItem
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:todos/by-id id])

  static om/IQuery
  (query [this]
    [:db/id :todo/completed :todo/title])

  Object
  (componentDidUpdate [this prev-props prev-state]
    (when (and (:todo/editing (om/props this))
               (om/get-state this :needs-focus))
      (let [node (dom/node this "editField")
            len  (.. node -primaryText -value -length)]
        (.focus node)
        (.setSelectionRange node len len))
      (om/update-state! this assoc :needs-focus nil)))

  (render [this]
    (let [props (om/props this)]
          ;{:keys [completed editing]} props]
          ;class (cond-> ""
          ;        completed (str "completed ")
          ;        editing   (str "editing")]
      (ui/list-item ;#js {:className class}
        {
         :primaryText (:todo/title props)
         :primaryTogglesNestedList true
         :onClick (fn [_]
                   (doto _ (.preventDefault) (.stopPropagation)))
         :onDoubleClick (fn [e] (edit this props))
         :leftCheckbox (checkbox this props)
         :rightIconButton (ui/icon-button (ic/action-delete))}))))

        ;(dom/div #js {:className "view"}
        ;  (checkbox this props)
        ;  (label this props)
        ;  (delete-button this props)}
        ;(edit-field this props)))))

(def item (om/factory TodoItem {:keyfn :db/id}))
