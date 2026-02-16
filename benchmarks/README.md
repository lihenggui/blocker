# `:benchmarks`

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :sync
    direction TB
    :sync:work[work]:::android-library
  end
  subgraph :feature
    direction TB
    :feature:appdetail[appdetail]:::android-feature
    :feature:applist[applist]:::android-feature
    :feature:debloater[debloater]:::android-feature
    :feature:generalrule[generalrule]:::android-feature
    :feature:ruledetail[ruledetail]:::android-feature
    :feature:search[search]:::android-feature
    :feature:settings[settings]:::android-feature
  end
  subgraph :core
    direction TB
    :core:analytics[analytics]:::android-library
    :core:common[common]:::android-library
    :core:component-controller[component-controller]:::android-library
    :core:data[data]:::android-library
    :core:database[database]:::android-library
    :core:datastore[datastore]:::android-library
    :core:datastore-proto[datastore-proto]:::jvm-library
    :core:designsystem[designsystem]:::android-library
    :core:domain[domain]:::android-library
    :core:git[git]:::android-library
    :core:ifw-api[ifw-api]:::android-library
    :core:model[model]:::android-library
    :core:network[network]:::android-library
    :core:provider[provider]:::android-library
    :core:rule[rule]:::android-library
    :core:ui[ui]:::android-library
  end
  :benchmarks[benchmarks]:::android-test
  :app-compose[app-compose]:::android-application

  :app-compose -.->|baselineProfile| :benchmarks
  :app-compose -.-> :core:analytics
  :app-compose -.-> :core:common
  :app-compose -.-> :core:data
  :app-compose -.-> :core:designsystem
  :app-compose -.-> :core:model
  :app-compose -.-> :core:network
  :app-compose -.-> :core:provider
  :app-compose -.-> :core:rule
  :app-compose -.-> :core:ui
  :app-compose -.-> :feature:appdetail
  :app-compose -.-> :feature:applist
  :app-compose -.-> :feature:debloater
  :app-compose -.-> :feature:generalrule
  :app-compose -.-> :feature:ruledetail
  :app-compose -.-> :feature:search
  :app-compose -.-> :feature:settings
  :app-compose -.-> :sync:work
  :benchmarks -.->|testedApks| :app-compose
  :core:common -.-> :core:model
  :core:component-controller -.-> :core:common
  :core:component-controller -.-> :core:ifw-api
  :core:component-controller -.-> :core:model
  :core:data -.-> :core:analytics
  :core:data --> :core:common
  :core:data -.-> :core:component-controller
  :core:data --> :core:database
  :core:data --> :core:datastore
  :core:data --> :core:network
  :core:database --> :core:model
  :core:datastore -.-> :core:common
  :core:datastore --> :core:datastore-proto
  :core:datastore --> :core:model
  :core:domain -.-> :core:common
  :core:domain -.-> :core:component-controller
  :core:domain --> :core:data
  :core:domain -.-> :core:ifw-api
  :core:domain --> :core:model
  :core:domain -.-> :core:rule
  :core:ifw-api -.-> :core:common
  :core:ifw-api -.-> :core:model
  :core:network --> :core:common
  :core:network --> :core:model
  :core:provider -.-> :core:analytics
  :core:provider -.-> :core:common
  :core:provider -.-> :core:data
  :core:provider -.-> :core:model
  :core:rule -.-> :core:common
  :core:rule -.-> :core:component-controller
  :core:rule -.-> :core:data
  :core:rule -.-> :core:ifw-api
  :core:ui --> :core:analytics
  :core:ui --> :core:designsystem
  :core:ui -.-> :core:domain
  :core:ui --> :core:model
  :feature:appdetail -.-> :core:component-controller
  :feature:appdetail -.-> :core:data
  :feature:appdetail -.-> :core:designsystem
  :feature:appdetail -.-> :core:domain
  :feature:appdetail -.-> :core:rule
  :feature:appdetail -.-> :core:ui
  :feature:applist -.-> :core:component-controller
  :feature:applist -.-> :core:data
  :feature:applist -.-> :core:designsystem
  :feature:applist -.-> :core:domain
  :feature:applist -.-> :core:ifw-api
  :feature:applist -.-> :core:ui
  :feature:debloater -.-> :core:data
  :feature:debloater -.-> :core:designsystem
  :feature:debloater -.-> :core:domain
  :feature:debloater -.-> :core:ui
  :feature:generalrule -.-> :core:data
  :feature:generalrule -.-> :core:designsystem
  :feature:generalrule -.-> :core:domain
  :feature:generalrule -.-> :core:ui
  :feature:ruledetail -.-> :core:component-controller
  :feature:ruledetail -.-> :core:data
  :feature:ruledetail -.-> :core:designsystem
  :feature:ruledetail -.-> :core:domain
  :feature:ruledetail -.-> :core:ui
  :feature:search -.-> :core:component-controller
  :feature:search -.-> :core:data
  :feature:search -.-> :core:designsystem
  :feature:search -.-> :core:domain
  :feature:search -.-> :core:ui
  :feature:settings -.-> :core:data
  :feature:settings -.-> :core:designsystem
  :feature:settings -.-> :core:domain
  :feature:settings -.-> :core:rule
  :feature:settings -.-> :core:ui
  :sync:work -.-> :core:analytics
  :sync:work -.-> :core:data
  :sync:work -.-> :core:git
  :sync:work -.-> :core:rule

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>Graph legend</summary>

```mermaid
graph TB
  application[application]:::android-application
  feature[feature]:::android-feature
  library[library]:::android-library
  jvm[jvm]:::jvm-library

  application -.-> feature
  library --> jvm

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->
