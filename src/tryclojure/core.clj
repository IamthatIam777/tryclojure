(ns tryclojure.core
  (:use ring.adapter.jetty
	net.licenser.sandbox
	[net.licenser.sandbox tester matcher]
	[hiccup core form-helpers page-helpers]
	[ring.middleware reload stacktrace params file session]
	net.cgrand.moustache
	[clojure.contrib.json.write]
	[clojure.stacktrace :only [root-cause]])
  (:import java.io.StringWriter
	   java.util.concurrent.TimeoutException))

(def sandbox-tester
     (extend-tester secure-tester 
		    (whitelist 
		     (function-matcher 'println 'print 'pr 'prn 'var 'print-doc 'doc 'throw))))

(def sc (stringify-sandbox (new-sandbox-compiler :tester sandbox-tester 
						 :timeout 1000)))

(defn execute-text [txt]  
    (try
     (let [writer (java.io.StringWriter.)
	   r ((sc txt) {'*out* writer})]
       {:result (str writer r)
	:type (str (type r))
	:expr txt})
     (catch TimeoutException _ 
       {:exception "Execution Timed Out!"})
     (catch SecurityException e 
       {:exception (str e)})
     (catch Exception e
       {:exception (str (.getMessage (root-cause e)))})))

(defn str-join [stuff] (apply str (interpose "\n" stuff)))

(defn fire-html [text]
  (html
   (:html5 doctype)
   
   [:head
    [:title "TryClojure"]
    (include-css "/resources/public/css/tryclojure.css")
    (include-js "/resources/public/js/jquery.js")    
    (include-js "/resources/public/js/jquery.console.js")
    (include-js "/resources/public/js/tryclojure.js")
    ]
   [:body
    [:h1 "Welcome to TryClojure!"]
    [:div#console {:class "console"}]]))

(defn repl-handler [{params :query-params session :session uri :uri :as request}]
  (pr request)
  (let [expr (params "expr")
	result (when (seq expr) (execute-text expr))]
    {:status  200
     :headers {"Content-Type" "text/json"}
     :body    (json-str result)}))

(def clojureroutes
     (app
      ;(wrap-reload '(tryclojure.core))
      (wrap-session)
      (wrap-file (System/getProperty "user.dir"))
      (wrap-params)
      (wrap-stacktrace)
      [""] handler
      ["clojure.json"] repl-handler))

(defn tryclj [] (run-jetty #'clojureroutes {:port 8081}))

(def *server-thread* (Thread. (fn [] (tryclj))))
(.start *server-thread*)