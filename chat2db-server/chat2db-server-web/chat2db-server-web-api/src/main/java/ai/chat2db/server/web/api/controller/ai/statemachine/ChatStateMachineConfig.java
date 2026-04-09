package ai.chat2db.server.web.api.controller.ai.statemachine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import ai.chat2db.server.web.api.controller.ai.statemachine.actions.AutoSelectTablesAction;
import ai.chat2db.server.web.api.controller.ai.statemachine.actions.BuildPromptAction;
import ai.chat2db.server.web.api.controller.ai.statemachine.actions.FetchSchemaAction;
import ai.chat2db.server.web.api.controller.ai.statemachine.actions.StreamAction;

@Configuration
@EnableStateMachineFactory
public class ChatStateMachineConfig extends StateMachineConfigurerAdapter<ChatState, ChatEvent> {

    @Autowired
    private AutoSelectTablesAction autoSelectTablesAction;

    @Autowired
    private FetchSchemaAction fetchSchemaAction;

    @Autowired
    private BuildPromptAction buildPromptAction;

    @Autowired
    private StreamAction streamAction;

    @Override
    public void configure(StateMachineStateConfigurer<ChatState, ChatEvent> states) throws Exception {
        states
            .withStates()
            .initial(ChatState.IDLE)
            .state(ChatState.AUTO_SELECTING_TABLES)
            .state(ChatState.FETCHING_TABLE_SCHEMA)
            .state(ChatState.BUILDING_PROMPT)
            .state(ChatState.STREAMING)
            .end(ChatState.COMPLETED)
            .end(ChatState.FAILED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ChatState, ChatEvent> transitions) throws Exception {
        transitions
            .withExternal()
                .source(ChatState.IDLE).target(ChatState.FETCHING_TABLE_SCHEMA)
                .event(ChatEvent.TABLES_PROVIDED)
                .action(fetchSchemaAction)
            .and()
            .withExternal()
                .source(ChatState.IDLE).target(ChatState.AUTO_SELECTING_TABLES)
                .event(ChatEvent.TABLES_NOT_PROVIDED)
                .action(autoSelectTablesAction)
            .and()
            .withExternal()
                .source(ChatState.AUTO_SELECTING_TABLES).target(ChatState.FETCHING_TABLE_SCHEMA)
                .event(ChatEvent.AUTO_SELECT_DONE)
                .action(fetchSchemaAction)
            .and()
            .withExternal()
                .source(ChatState.AUTO_SELECTING_TABLES).target(ChatState.FAILED)
                .event(ChatEvent.AUTO_SELECT_FAILED)
            .and()
            .withExternal()
                .source(ChatState.FETCHING_TABLE_SCHEMA).target(ChatState.BUILDING_PROMPT)
                .event(ChatEvent.SCHEMA_FETCHED)
                .action(buildPromptAction)
            .and()
            .withExternal()
                .source(ChatState.FETCHING_TABLE_SCHEMA).target(ChatState.FAILED)
                .event(ChatEvent.FETCH_SCHEMA_FAILED)
            .and()
            .withExternal()
                .source(ChatState.BUILDING_PROMPT).target(ChatState.STREAMING)
                .event(ChatEvent.PROMPT_BUILT)
                .action(streamAction)
            .and()
            .withExternal()
                .source(ChatState.BUILDING_PROMPT).target(ChatState.FAILED)
                .event(ChatEvent.PROMPT_BUILD_FAILED)
            .and()
            .withExternal()
                .source(ChatState.STREAMING).target(ChatState.COMPLETED)
                .event(ChatEvent.STREAM_FINISHED)
            .and()
            .withExternal()
                .source(ChatState.STREAMING).target(ChatState.FAILED)
                .event(ChatEvent.AI_CALL_FAILED)
            .and()
            .withExternal()
                .source(ChatState.STREAMING).target(ChatState.FAILED)
                .event(ChatEvent.CANCEL);
    }
}