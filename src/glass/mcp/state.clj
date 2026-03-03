(ns glass.mcp.state)

(def protocol-version "2025-06-18")

(defn initial-state
  []
  {:sessions           {}
   :resources          {}
   :tools              {}
   :resource-templates {}
   :prompts            {}
   :protocol-version   protocol-version
   :server-info        {:name    "Clojure MCP SDK"
                        :title   "Pure Clojure MCP server"
                        :version "1.0.0"}
   :capabilities       {:logging   {}
                        :prompts   {:listChanged true}
                        :resources {:listChanged true}
                        :tools     {:listChanged true}}
   :instructions       ""})

(defonce state (atom (initial-state)))

(defn add-prompt
  [{:keys [name title description arguments messages-fn]}]
  (swap! state update :prompts assoc name
         {:title title
          :description description
          :arguments arguments
          :messages-fn messages-fn}))

(defn add-resource
  [{:keys [uri name title description mime-type load-fn]}]
  (swap! state update :resources assoc uri
         {:name name
          :title title
          :description description
          :mimeType mime-type
          :load-fn load-fn}))

(defn add-tool
  [{:keys [name title description schema tool-fn]}]
  (swap! state update :tools assoc name
         {:title title
          :description description
          :inputSchema schema
          :tool-fn tool-fn}))

(defn set-instructions
  [instructions]
  (swap! state assoc :instructions instructions))
