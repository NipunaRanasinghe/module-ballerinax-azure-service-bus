// Copyright (c) 2023 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations

import ballerina/log;
import ballerinax/asb;

asb:SqlRule rule = {
    filter: "1=1",
    action: "SET a = 'b'"
};
asb:CreateRuleOptions ruleConfig = {
    rule: rule
};
// Connection Configurations
configurable string connectionString = ?;

// This sample demonstrates a scenario where azure service bus connecter is used to 
// create a rule for a subscription.
public function main() returns error? {
    log:printInfo("Initializing Asb admin client...");
    asb:ASBAdministration adminClient = check new (connectionString);
    asb:RuleProperties? rule = check adminClient->createRule("test-topic", "test-subscription", "test-rule", ruleConfig);
    if (rule is asb:RuleProperties) {
        log:printInfo(rule.toString());
    } else {
        log:printError(rule.toString());
    }
}
