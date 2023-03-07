;; TODO: Clean up this NS, especially the styling, & factor out react components
;; TODO: Input box within the result box itself, so not all entry has to be via
;;       the URL bar
;; TODO: Capture stdout too
;; TODO: Or maybe each thing could spawn its own window? And then further
;;       manipulation happens via the input bar? That way you could query a
;;       couple books and line them up however you want.
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
(defonce state (r/atom {:msgs [] :window-state :hidden}))

(def min-width 300)
(def min-height 62)

(defn window-drag-handler
  "Build an onmousedown handler for the window's drag handle that modifies the
  given atoms to change the window's position."
  [window-top window-left]
  (fn [e]
    ; track the initial offset between the window pos and the cursor
    (let [dy (- @window-top (.-pageY e))
          dx (- @window-left (.-pageX e))
          on-move (fn [event]
                    ; move the window by the cursor's change + initial offset
                    (reset! window-top (max 0 (+ (.-pageY event) dy)))
                    (reset! window-left (max 0 (+ (.-pageX event) dx))))]
      ; do this in mousemove until the mouse is released
      (js/window.addEventListener "mousemove" on-move)
      (js/window.addEventListener "mouseup"
        (fn on-mouse-up [_e]
          (js/window.removeEventListener "mousemove" on-move)
          (js/window.removeEventListener "mouseup" on-mouse-up)))
      (.preventDefault e))))

(defn window-resize-handle
  "Build an onmousedown handler for the window's resize handle that modifies the
  given atoms to change the window's size."
  [window-width window-height]
  (fn [e]
    (let [dy (- @window-height (.-pageY e))
          dx (- @window-width (.-pageX e))
          on-move (fn [event]
                    (reset! window-width (max min-width (+ (.-pageX event) dx)))
                    (reset! window-height (max min-height (+ (.-pageY event) dy))))]
      (js/window.addEventListener "mousemove" on-move)
      (js/window.addEventListener "mouseup"
        (fn on-mouse-up [_e]
          (js/window.removeEventListener "mousemove" on-move)
          (js/window.removeEventListener "mouseup" on-mouse-up)))
      (.preventDefault e))))

(defn toggle-window-collapse [_e]
  (swap! state update :window-state {:collapsed :normal :normal :collapsed}))

(defn window-title-bar
  "A title bar with three evenly spaced buttons, drawn as SVGs, to collapse
  drag or hide the window."
  [window-width window-top window-left collapsed?]
  ; Make it slightly wider and shift left so that the top is rounded,
  ; but the bottom is not
  [:div.window-header {:style {:width (+ @window-width 4)}}
   [:div.taskbar-button {:style {:padding-left 8}
                         :onClick toggle-window-collapse}
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :viewBox "0 0 9 5"
           :width "12"
           :height "8"
           :style {:transform (if collapsed? "rotate(-90deg)" "rotate(-0deg)")}}
     [:path {:d "M3.8 4.4c.4.3 1 .3 1.4 0L8 1.7A1 1 0 007.4 0H1.6a1 1 0 00-.7 1.7l3 2.7z"}]]]

   [:div.taskbar-button
    {:style {:cursor :move}
     :onMouseDown (window-drag-handler window-top window-left)}
    [:svg {:width "20"
           :height "10"
           :viewBox "0 0 28 14"
           :xmlns "http://www.w3.org/2000/svg"}
     [:circle {:cx "2" :cy "2" :r "2"}]
     [:circle {:cx "14" :cy "2" :r "2"}]
     [:circle {:cx "26" :cy "2" :r "2"}]
     [:circle {:cx "2" :cy "12" :r "2"}]
     [:circle {:cx "14" :cy "12" :r "2"}]
     [:circle {:cx "26" :cy "12" :r "2"}]]]

   [:div.taskbar-button
    {:style {:padding-right 8 :top 1}
     :onClick (fn [_] (swap! state assoc :window-state :hidden))}
    [:svg {:width 20 :height 20 :viewBox "0 0 20 20" :fill "white"}
     [:rect {:x "4" :y "9" :width "12" :height "2" :rx "1"}]]]])

(defn window [contents]
  (let [window-width (r/atom 300)
        window-height (r/atom 200)
        window-top (r/atom 25)
        window-left (r/atom 25)
        hover? (r/atom false)]
    ; n.b. style is in float_results_on_page.css, *except* for dynamically
    ; computed styles (like window position)
    (fn [contents]
      (let [{:keys [window-state]} @state
            collapsed? (= window-state :collapsed)]
        (when-not (= window-state :hidden)
          [:div.window-container
           {:style {:width @window-width
                    :height (if collapsed? :fit-content @window-height)
                    :top @window-top
                    :left @window-left}
            :onMouseOver (fn [_] (reset! hover? true))
            :onMouseOut (fn [_] (reset! hover? false))}

           [window-title-bar window-width window-top window-left collapsed?]

           (when-not collapsed?
             [:div#com-mjdowney-scx-float-results-on-page-window-content.window-content
              {:style {:height (- @window-height 32)}}
              (when @hover?
                [:div.window-resize
                 {:style {:top (+ @window-top @window-height -10)
                          :left (+ @window-left @window-width -10)}
                  :onMouseDown (window-resize-handle window-width window-height)}
                 [:div.window-resize-decoration]])
              contents])])))))

(defn app []
  [window
   (for [[idx {:keys [in out]}] (map-indexed vector (:msgs @state))]
     [:div {:key idx :style {:display :block}}
      [:br] [:pre in]
      [:br] [:pre ";=> " out]])])

(defn on-message [msg _sender _respondf]
  (when (= (.-type msg) (str :com.mjdowney.scx.service-worker/msg))
    (let [data (.-data msg)
          ret (sci/eval-with-sci data)]
      (js/console.log "Got:" data)
      (js/console.log ";=>" ret)
      (swap! state
        (fn [state]
          (-> state
              (assoc :window-state :normal)
              (update :msgs conj {:in data :out ret}))))
      (js/window.requestAnimationFrame
        (fn []
          (let [container (js/document.getElementById "com-mjdowney-scx-float-results-on-page-window-content")]
            (set! (.-scrollTop container) (.-scrollHeight container))))))))

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
