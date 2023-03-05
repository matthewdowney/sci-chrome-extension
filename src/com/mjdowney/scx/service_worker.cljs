(ns com.mjdowney.scx.service-worker
  (:require [cljs.analyzer.api]
            [cljs.core.async :refer [go] :as async]
            [cljs.core.async.interop :refer [<p!]]
            [clojure.string :as string]
            [sci.core :as sci]))

(def safe-pr-result
  "Macro for use inside the SCI execution environment which truncates infinite
  return values."
  ^:sci/macro
  (fn [_&form _&env & body]
    `(let [ret# (do ~@body)]
       (if (and (sequential? ret#) (>= (bounded-count 50 ret#) 50))
         (pr-str (concat (take 50 ret#) ['...]))
         (pr-str ret#)))))

(def sci-ctx
  "Context of SCI execution, persistent between different calls."
  (sci/init {:bindings {'safe-pr-result safe-pr-result}}))

(defn eval-with-sci
  "Eval the code in string `s` with SCI and return a string result."
  [s]
  (try
    (sci/eval-string* sci-ctx (str "(safe-pr-result " s " )"))
    (catch :default e (str e))))

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
                 (pprint-omnibox-result (eval-with-sci to-eval)))]
      ; extra space in :content is a hack so that the browser displays the
      ; description (incl ;=> ...) even with a literal match
      (suggestf #js [#js {:content (str to-eval " ") :description desc}]))))

(defn get-this-tab [] (.then (js/chrome.tabs.query #js {:active true :currentWindow true}) first))
(defn send-message [tab-id msg] (js/chrome.tabs.sendMessage tab-id msg))
(defn inject-script [tab-id path]
  (let [data (clj->js {:target {:tabId tab-id} :files [path]})]
    (js/console.log "Injecting script: " data)
    (js/chrome.scripting.executeScript data)))

(defn on-input-entered [text]
  (js/console.log "Input entered: " text)
  (go
    (try
      (let [tab (<p! (get-this-tab))]
        (js/console.log "Got tab:" tab)
        (if (and tab (not (string/starts-with? (.-url tab) "chrome://")))
          (do
            (<p! (inject-script (.-id tab) "test.js"))
            (send-message (.-id tab) #js {:type "msg" :data text}))
          ;; TODO: Open custom HTML here, since the target page can't be modified
          (js/console.log "Ignoring tab because we cannot inject JS here...")))
      (catch js/Error err
        ;; TODO: Open custom HTML here, since the target page can't be modified
        (js/console.log err)))))

;; TODO: Inject a script when a result is selected, set some var in the window,
;;       and don't inject on subsequent calls if this var is truthy
;; TODO: Generate the script from CLJS, make it accept messages from this NS and
;;       display the SCI results
;; TODO: Does this work more nicely with promesa?
;; TODO: Add user hooks to allow writing e.g. (* 75m (+ 1 10bp))
(defn init []
  (js/chrome.omnibox.onInputChanged.addListener on-input-changed)
  (js/chrome.omnibox.onInputEntered.addListener on-input-entered))
