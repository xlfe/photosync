(ns photosync.parser
  (:require [om.next :as om]))

;; =============================================================================
;; Reads

(defmulti read om/dispatch)
(def log (.-log js/console))

(defmethod read :default
  [{:keys [state]} k _]
  (let [st @state]
    (if (contains? st k)
      {:value (get st k)}
      {:remote true})))



;; =============================================================================
;; Mutations

(defmulti mutate om/dispatch)

(defmethod mutate :default
  [_ _ _] {:remote true})

(defmethod mutate 'todos/clear
  [{:keys [state]} _ _]
  {:remote true
   :action
   (fn []
     (let [st @state]
       (swap! state update-in [:todos/list]
         (fn [list]
           (into []
             (remove #(get-in st (conj % :todo/completed)))
             list)))))})

(defmethod mutate 'todos/toggle-all
  [{:keys [state]} _ {:keys [value]}]
  {:action
   (fn []
     (letfn [(step [state' ref]
               (update-in state' ref assoc
                 :todo/completed value))]
       (swap! state
         #(reduce step % (:todos/list %)))))})

(defmethod mutate 'todo/update
  [{:keys [state ref]} _ new-props]
  {:remote true
   :action ;; OPTIMISTIC UPDATE
   (fn []
     (swap! state update-in ref merge new-props))})

(defmethod mutate 'todos/create-temp
  [{:keys [state]} _ new-todo]
  {:value [:todos/list]
   :action (fn [] (swap! state assoc :todos/temp new-todo))})

(defmethod mutate 'services/delete
  [{:keys [state]} _ {:keys [key]}]
  {
   :remote true
   ; Why doesnt this work here?!
   ; Todo: figure it out! why it doesn't work..
   ;:value {:keys :services/list}
   :action (fn []
             (let [st @state]
               (swap! state update-in [:services/list]
                  (fn [list]
                    (into []
                      (remove #(= (:key %) key))
                      list)))))})
