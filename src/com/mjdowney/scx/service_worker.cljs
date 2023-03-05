(ns com.mjdowney.scx.service-worker
  (:require [cljs.analyzer.api]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [clojure.string :as string]
            [com.mjdowney.scx.sci :as sci]))

(defn balance-parens
  "Return a series of ], ), or } characters necessary to balance the
  parens and brackets in the given EDN string."
  [s]
  (let [length (.-length s)]
    (letfn [(pop [xs x']
              (lazy-seq
                (if-let [x (first xs)]
                  (if (= x x')
                    (rest xs)
                    (cons x (pop (rest xs) x'))))))]
      (loop [idx 0
             closing '()]
        (if (== idx length)
          closing
          (recur
            (inc idx)
            (case (.charAt s idx)
              \( (conj closing \))
              \) (pop closing \))
              \{ (conj closing \})
              \} (pop closing \})
              \[ (conj closing \])
              \] (pop closing \])
              closing)))))))

(def xml-escapes
  {\" "&quot;"
   \' "&apos;"
   \< "&lt;"
   \> "&gt;"
   \& "&amp;"})

(defn pprint-omnibox-result [result-str]
  (str "<dim> ;=> " (string/escape result-str xml-escapes) "</dim>"))

(defn on-input-changed [text suggestf]
  (when (seq text)
    (let [suffix-chars (balance-parens text)
          suffix (apply str suffix-chars)
          to-eval (str text suffix)
          desc (str "<match>" text "</match>" suffix
                 (pprint-omnibox-result (sci/eval-with-sci to-eval)))]
      ; extra space in :content is a hack so that the browser displays the
      ; description (incl ;=> ...) even with a literal match
      (suggestf #js [#js {:content (str to-eval " ") :description desc}]))))

(defn get-this-tab [] (.then (js/chrome.tabs.query #js {:active true :currentWindow true}) first))
(defn send-message [tab-id msg] (js/chrome.tabs.sendMessage tab-id msg))

(defn on-input-entered [text]
  (js/console.log "Input entered:" text)
  (go
    (try
      (let [tab (<p! (get-this-tab))]
        (js/console.log "Got tab:" tab)
        (if (and tab (not (string/starts-with? (.-url tab) "chrome://")))
          (send-message (.-id tab) (clj->js {:type (str ::msg) :data text}))
          ;; TODO: Open custom HTML here, since the target page can't be modified
          (js/console.log "Ignoring tab because we cannot inject JS here...")))
      (catch js/Error err
        ;; TODO: Open custom HTML here, since the target page can't be modified
        (js/console.log err)))))

;; TODO: Add user hooks to allow writing e.g. (* 75m (+ 1 10bp))
(defn init []
  (js/chrome.omnibox.onInputChanged.addListener on-input-changed)
  (js/chrome.omnibox.onInputEntered.addListener on-input-entered))
