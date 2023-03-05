(ns com.mjdowney.scx.float-results-on-page
  (:require [clojure.string :as string]
            [com.mjdowney.scx.sci :as sci]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

(defonce window-global-to-check-if-already-injected (atom false))

(def xml-escapes {\" "&quot;" \' "&apos;" \< "&lt;" \> "&gt;" \& "&amp;"})
(defn escape-xml [s] (string/escape s xml-escapes))

(def ^:const app-id "com.mjdowney.scx.float-results-on-page")
(defonce state (r/atom {:msgs [] :window-state :normal}))

(defn app []
  (let [window-width (r/atom 300)
        window-height (r/atom 200)
        window-top (r/atom 25)
        window-left (r/atom 25)
        hover? (r/atom false)]
    (fn []
      (let [{:keys [window-state msgs]} @state
            collapsed? (= window-state :collapsed)]
        (when-not (= window-state :hidden)
          [:div.window-container
           {:style {:width @window-width
                    :height (if collapsed? :fit-content @window-height)
                    :top @window-top
                    :left @window-left
                    :position :fixed
                    :background-color "white"
                    :border "1px solid black"
                    :overflow "hidden"
                    :z-index 2147483646}
            :onMouseOver (fn [e] (reset! hover? true))
            :onMouseOut (fn [e] (reset! hover? false))}

           [:div.window-header
            {:style {:width @window-width
                     :height 32
                     :background-color "black"
                     :color "white"
                     :font-family "system-ui"
                     :display "flex"
                     :justify-content "space-between"
                     :align-items "center"}}

            [:div {:style {:top -2
                           :position :relative
                           :padding-left 8
                           :cursor :pointer}
                   :onClick (fn [e] (swap! state update :window-state {:collapsed :normal :normal :collapsed}))}
             [:svg {:xmlns "http://www.w3.org/2000/svg"
                    :viewBox "0 0 9 5"
                    :width "12"
                    :height "8"
                    :style {:transform (if collapsed?
                                         "rotate(-90deg)"
                                         "rotate(-0deg)")}}
              [:path {:fill "white"
                      :d "M3.8 4.4c.4.3 1 .3 1.4 0L8 1.7A1 1 0 007.4 0H1.6a1 1 0 00-.7 1.7l3 2.7z"}]]]

            [:div {:style {:top -1
                           :cursor :move
                           :position :relative}
                   :onMouseDown
                   (fn [e]
                     (let [dy (- @window-top (.-pageY e))
                           dx (- @window-left (.-pageX e))
                           on-move (fn [event]
                                     (swap! window-top #(max 0 (+ (.-pageY event) dy)))
                                     (swap! window-left #(max 0 (+ (.-pageX event) dx))))]
                       (js/window.addEventListener "mousemove" on-move)
                       (js/window.addEventListener "mouseup"
                         (fn on-mouse-up [e]
                           (js/window.removeEventListener "mousemove" on-move)
                           (js/window.removeEventListener "mouseup" on-mouse-up)))
                       (.preventDefault e)))}
             [:svg {:width "20"
                    :height "10"
                    :viewBox "0 0 28 14"
                    :xmlns "http://www.w3.org/2000/svg"
                    :style {:fill :white}}
              [:circle {:cx "2" :cy "2" :r "2"}]
              [:circle {:cx "14" :cy "2" :r "2"}]
              [:circle {:cx "26" :cy "2" :r "2"}]
              [:circle {:cx "2" :cy "12" :r "2"}]
              [:circle {:cx "14" :cy "12" :r "2"}]
              [:circle {:cx "26" :cy "12" :r "2"}]]]

            [:div {:style {:position :relative
                           :padding-right 8
                           :cursor :pointer
                           :fill "white"
                           :top 1}
                   :onClick (fn [e] (swap! state assoc :window-state :hidden))}
             [:svg {:width 20 :height 20 :viewBox "0 0 20 20" :fill "white"}
              [:rect {:x "4" :y "9" :width "12" :height "2" :rx "1"}]]]]

           (when-not collapsed?
             [:div.window-content {:style {:padding 15
                                           :display :block
                                           :height (- @window-height 32 30)
                                           :position :relative}}
              (when @hover?
                [:div.window-resize
                 {:style {:display :block
                          :position :absolute
                          :bottom 3
                          :right 3
                          :width 8
                          :height 8
                          #_#_:background-color :lightgray
                          :cursor :nwse-resize}

                  :onMouseDown
                  (fn [e]
                    (let [dy (- @window-height (.-pageY e))
                          dx (- @window-width (.-pageX e))
                          on-move (fn [event]
                                    (reset! window-width (max 150 (+ (.-pageX event) dx)))
                                    (reset! window-height (max 32 (+ (.-pageY event) dy))))]
                      (js/window.addEventListener "mousemove" on-move)
                      (js/window.addEventListener "mouseup"
                        (fn on-mouse-up [e]
                          (js/window.removeEventListener "mousemove" on-move)
                          (js/window.removeEventListener "mouseup" on-mouse-up)))
                      (.preventDefault e)))}

                 [:div {:style {:position "absolute"
                                :top "50%"
                                :left "50%"
                                :transform "translate(-50%, -50%)"
                                :border-bottom "1px solid black"
                                :border-right "1px solid black"
                                :width "10px"
                                :height "10px"}}]])
              [:h3 "SCI eval results:"]
              (when-let [recent (peek (:msgs @state))]
                [:<>
                 [:pre (:in recent)]
                 [:pre ";=> " (:out recent)]])])])))))

(defn on-message [msg sender respondf]
  (when (= (.-type msg) (str :com.mjdowney.scx.service-worker/msg))
    (let [data (.-data msg)
          ret (sci/eval-with-sci data)]
      (js/console.log "Got:" data)
      (js/console.log ";=>" ret)
      (swap! state
        (fn [state]
          (-> state
              (assoc :window-state :normal)
              (update :msgs conj {:in data :out ret})))))))

(defn inject-stylesheet [href]
  (let [link (gdom/createElement "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-type link) "text/css")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn inject-react-app []
  (let [react-app-ele (gdom/createElement "div")]
    (set! (.-id react-app-ele) app-id)
    (.appendChild (.-body js/document) react-app-ele)
    (rdom/render [app] react-app-ele)))

(defn init []
  (when-not @window-global-to-check-if-already-injected
    (reset! window-global-to-check-if-already-injected true)
    (js/chrome.runtime.onMessage.addListener on-message)
    (inject-stylesheet (js/chrome.runtime.getURL "float_results_on_page.css"))
    (inject-react-app)
    (js/console.log "Injected SCI result viewer.")))
