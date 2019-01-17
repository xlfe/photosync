(ns photosync.jobs
  (:require [clojure.string :as string]
            [cljs-material-ui.core :as ui]
            [cljs-material-ui.icons :as ic]           ; SVG icons that comes with MaterialUI
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [photosync.util :refer [hidden pluralize]]))


;; -----------------------------------------------------------------------------
;; Job


(defn delete-button [c {:keys [db/id]}]
  (dom/button
    #js {
         :onClick (fn [_] (om/transact! c `[(todo/delete {:db/id ~id})]))}))

(defui ^:once JobItem
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:jobs/by-id id])

  static om/IQuery
  (query [this]
    [:db/id])

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
         :rightIconButton (ui/icon-button (ic/action-delete))}))))

        ;(dom/div #js {:className "view"}
        ;  (checkbox this props)
        ;  (label this props)
        ;  (delete-button this props)}
        ;(edit-field this props)))))

(def job (om/factory JobItem {:keyfn :db/id}))

