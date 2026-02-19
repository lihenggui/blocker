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
  subgraph :feature
    direction TB
    subgraph :feature:applist
      direction TB
      :feature:applist:api[api]:::android-library
      :feature:applist:impl[impl]:::android-library
    end
    subgraph :feature:debloater
      direction TB
      :feature:debloater:api[api]:::android-library
      :feature:debloater:impl[impl]:::android-library
    end
    subgraph :feature:generalrule
      direction TB
      :feature:generalrule:api[api]:::android-library
      :feature:generalrule:impl[impl]:::android-library
    end
    subgraph :feature:search
      direction TB
      :feature:search:api[api]:::android-library
      :feature:search:impl[impl]:::android-library
    end
    subgraph :feature:settings
      direction TB
      :feature:settings:api[api]:::android-library
      :feature:settings:impl[impl]:::android-library
    end
    subgraph :feature:appdetail
      direction TB
      :feature:appdetail:api[api]:::android-library
      :feature:appdetail:impl[impl]:::android-library
    end
    subgraph :feature:ruledetail
      direction TB
      :feature:ruledetail:api[api]:::android-library
      :feature:ruledetail:impl[impl]:::android-library
    end
  end
  subgraph :sync
    direction TB
    :sync:work[work]:::android-library
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
    :core:navigation[navigation]:::android-library
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
  :app-compose -.-> :core:navigation
  :app-compose -.-> :core:network
  :app-compose -.-> :core:provider
  :app-compose -.-> :core:rule
  :app-compose -.-> :core:ui
  :app-compose -.-> :feature:appdetail:api
  :app-compose -.-> :feature:appdetail:impl
  :app-compose -.-> :feature:applist:api
  :app-compose -.-> :feature:applist:impl
  :app-compose -.-> :feature:debloater:api
  :app-compose -.-> :feature:debloater:impl
  :app-compose -.-> :feature:generalrule:api
  :app-compose -.-> :feature:generalrule:impl
  :app-compose -.-> :feature:ruledetail:api
  :app-compose -.-> :feature:ruledetail:impl
  :app-compose -.-> :feature:search:api
  :app-compose -.-> :feature:search:impl
  :app-compose -.-> :feature:settings:api
  :app-compose -.-> :feature:settings:impl
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
  :feature:appdetail:api --> :core:navigation
  :feature:appdetail:api -.-> :core:ui
  :feature:appdetail:impl -.-> :core:component-controller
  :feature:appdetail:impl -.-> :core:data
  :feature:appdetail:impl -.-> :core:designsystem
  :feature:appdetail:impl -.-> :core:domain
  :feature:appdetail:impl -.-> :core:rule
  :feature:appdetail:impl -.-> :core:ui
  :feature:appdetail:impl -.-> :feature:appdetail:api
  :feature:appdetail:impl -.-> :feature:ruledetail:api
  :feature:applist:api --> :core:navigation
  :feature:applist:api -.-> :core:ui
  :feature:applist:impl -.-> :core:component-controller
  :feature:applist:impl -.-> :core:data
  :feature:applist:impl -.-> :core:designsystem
  :feature:applist:impl -.-> :core:domain
  :feature:applist:impl -.-> :core:ifw-api
  :feature:applist:impl -.-> :core:ui
  :feature:applist:impl -.-> :feature:appdetail:api
  :feature:applist:impl -.-> :feature:applist:api
  :feature:applist:impl -.-> :feature:settings:api
  :feature:debloater:api --> :core:navigation
  :feature:debloater:api -.-> :core:ui
  :feature:debloater:impl -.-> :core:data
  :feature:debloater:impl -.-> :core:designsystem
  :feature:debloater:impl -.-> :core:domain
  :feature:debloater:impl -.-> :core:ui
  :feature:debloater:impl -.-> :feature:debloater:api
  :feature:generalrule:api --> :core:navigation
  :feature:generalrule:api -.-> :core:ui
  :feature:generalrule:impl -.-> :core:data
  :feature:generalrule:impl -.-> :core:designsystem
  :feature:generalrule:impl -.-> :core:domain
  :feature:generalrule:impl -.-> :core:ui
  :feature:generalrule:impl -.-> :feature:generalrule:api
  :feature:generalrule:impl -.-> :feature:ruledetail:api
  :feature:ruledetail:api --> :core:navigation
  :feature:ruledetail:api -.-> :core:ui
  :feature:ruledetail:impl -.-> :core:component-controller
  :feature:ruledetail:impl -.-> :core:data
  :feature:ruledetail:impl -.-> :core:designsystem
  :feature:ruledetail:impl -.-> :core:domain
  :feature:ruledetail:impl -.-> :core:ui
  :feature:ruledetail:impl -.-> :feature:appdetail:api
  :feature:ruledetail:impl -.-> :feature:ruledetail:api
  :feature:search:api -.-> :core:component-controller
  :feature:search:api -.-> :core:data
  :feature:search:api -.-> :core:domain
  :feature:search:api --> :core:navigation
  :feature:search:api -.-> :core:ui
  :feature:search:impl -.-> :core:component-controller
  :feature:search:impl -.-> :core:data
  :feature:search:impl -.-> :core:designsystem
  :feature:search:impl -.-> :core:domain
  :feature:search:impl -.-> :core:ui
  :feature:search:impl -.-> :feature:appdetail:api
  :feature:search:impl -.-> :feature:ruledetail:api
  :feature:search:impl -.-> :feature:search:api
  :feature:settings:api --> :core:navigation
  :feature:settings:api -.-> :core:ui
  :feature:settings:impl -.-> :core:data
  :feature:settings:impl -.-> :core:designsystem
  :feature:settings:impl -.-> :core:domain
  :feature:settings:impl -.-> :core:rule
  :feature:settings:impl -.-> :core:ui
  :feature:settings:impl --> :feature:settings:api
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
