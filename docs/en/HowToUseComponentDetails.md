# What is Component Details
The component details feature was officially added to Blocker after version 2.0.2842. This feature helps users quickly view detailed information about components, assisting them in understanding the role of the component and confirming whether it is necessary for the application.

# Categories of Components
## Blocker Built-in Rules
Blocker's built-in rules are stored in [blocker-general-rules](https://github.com/lihenggui/blocker-general-rules). The app keeps a separate offline version of these rules for use when there is no internet connection or when updates cannot be fetched.

The components folder in the repository contains information descriptions of in-app components, while the rules folder describes general rules. To support multiple languages, rules for different languages are stored in separate folders.

The application checks for updates from this repository during its first launch each day. If there are updates, they are downloaded and stored locally.

If there are matching component rules in the application, Blocker will automatically add an introduction for that component when displaying the application's components. If users want to view detailed information or modify component details, they can click on the component and make changes in the popup dialog.

## User Custom Rules
If the application does not have the component rules users desire, and if users know the source of the component, they can add custom rules within the application. By default, user custom rules are saved in the application's storage, and Blocker synchronizes them when reading component information.

If users want to share rules with third parties, they can click the share button on the application details screen and send the rule's compressed package to the author for inclusion in Blocker rules. For advanced users, they can also directly submit a pull request to add rules to the [rules repository](https://github.com/lihenggui/blocker-general-rules) on GitHub.

Blocker prioritizes displaying user-added custom rules. If custom rules are not available, it will display the software's built-in rules.