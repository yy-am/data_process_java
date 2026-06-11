// NOTE:
// This file is a patched version of the user's pasted myChatModel source.
// It is intentionally placed at the repo root as a reference implementation,
// because the actual custom ChatModel project is not the current workspace.
// Unchanged imports / package declaration should be aligned in the target project.

public class myChatModel {

    public myChatModel(myCopilotApi myCopilotApi, myChatOptions defaultOptions,
                        ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
                        ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
        Assert.notNull(myCopilotApi, "myCopilotApi cannot be null");
        Assert.notNull(defaultOptions, "defaultOptions cannot be null");
        Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
        Assert.notNull(retryTemplate, "retryTemplate cannot be null");
        Assert.notNull(observationRegistry, "observationRegistry cannot be null");
        Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
        this.myCopilotApi = myCopilotApi;
        this.defaultOptions = defaultOptions;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return this.internalCall(requestPrompt, null);
    }

    public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

        myCopilotApi.ChmyompletionRequest request = createRequest(prompt, false);

        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
            .prompt(prompt)
            .provider(myConstants.PROVIDER_NAME)
            .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
            .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                    this.observationRegistry)
            .observe(() -> {

                ResponseEntity<myCopilotApi.Chmyompletion> completionEntity = this.retryTemplate
                    .execute(ctx -> this.myCopilotApi.chmyompletionEntity(request));

                var chmyompletion = completionEntity.getBody();

                if (chmyompletion == null) {
                    logger.warn("No chat completion returned for prompt: {}", prompt);
                    return new ChatResponse(List.of());
                }

                List<myCopilotApi.Chmyompletion.Choice> choices = chmyompletion.choices();
                if (choices == null) {
                    logger.warn("No choices returned for prompt: {}", prompt);
                    return new ChatResponse(List.of());
                }

                List<Generation> generations = choices.stream().map(choice -> {
                    Map<String, Object> metadata = Map.of(
                            "id", chmyompletion.id() != null ? chmyompletion.id() : "",
                            "role", choice.message().role() != null ? choice.message().role().name() : "",
                            "index", choice.index(),
                            "finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
                    return buildGeneration(choice, metadata);
                }).toList();
                myCopilotApi.Usage usage = chmyompletion.usage();
                Usage currentChatResponseUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
                Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
                        previousChatResponse);
                ChatResponse chatResponse = new ChatResponse(generations,
                        from(completionEntity.getBody(), accumulatedUsage));

                observationContext.setResponse(chatResponse);

                return chatResponse;

            });
        return buildResponse(response, prompt);
    }

    private ChatResponse buildResponse(ChatResponse response, Prompt prompt) {
        if (shouldExecuteTools(prompt, response)) {
            var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
            if (toolExecutionResult.returnDirect()) {
                return ChatResponse.builder()
                        .from(response)
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .build();
            }
            return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
                    response);
        }
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return internalStream(requestPrompt, null);
    }

    public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
        return Flux.deferContextual(contextView -> {
            myCopilotApi.ChmyompletionRequest request = createRequest(prompt, true);

            Flux<myCopilotApi.ChmyompletionChunk> completionChunks = this.myCopilotApi.chmyompletionStream(request);
            ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
            StreamingToolCallAccumulator toolCallAccumulator = new StreamingToolCallAccumulator();

            final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(myConstants.PROVIDER_NAME)
                .build();

            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
                    this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                    this.observationRegistry);

            observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

            Flux<ChatResponse> chunkResponses =
                    getChatResponseFlux(previousChatResponse, completionChunks, roleMap, toolCallAccumulator);

            Flux<ChatResponse> flux = chunkResponses.flatMap(response -> {
                if (shouldExecuteTools(prompt, response)) {
                    return Flux.defer(() -> {
                        var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
                        if (toolExecutionResult.returnDirect()) {
                            return Flux.just(ChatResponse.builder().from(response)
                                    .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                    .build());
                        }
                        return this.internalStream(
                                new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
                                response
                        );
                    }).subscribeOn(Schedulers.boundedElastic());
                }
                return Flux.just(response);
            })
            .doOnError(observation::error)
            .doFinally(s -> observation.stop())
            .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

            return new MessageAggregator().aggregate(flux, observationContext::setResponse);
        });
    }

    private Flux<ChatResponse> getChatResponseFlux(
            ChatResponse previousChatResponse,
            Flux<myCopilotApi.ChmyompletionChunk> completionChunks,
            ConcurrentHashMap<String, String> roleMap,
            StreamingToolCallAccumulator toolCallAccumulator
    ) {
        return completionChunks.map(chunk -> {
            try {
                String id = chunk.id();
                myCopilotApi.Chmyompletion chmyompletion = chunkToChmyompletion(chunk);

                List<Generation> generations = chmyompletion.choices().stream().map(choice -> {
                    if (choice.message().role() != null) {
                        roleMap.putIfAbsent(id, choice.message().role().name());
                    }

                    List<myCopilotApi.ChmyompletionMessage.ToolCall> mergedToolCalls =
                            toolCallAccumulator.merge(id, choice.index(), choice.message().toolCalls());

                    Map<String, Object> metadata = Map.of(
                            "id", chmyompletion.id(),
                            "role", roleMap.getOrDefault(id, ""),
                            "finishReason", choice.finishReason() != null ? choice.finishReason().name() : ""
                    );

                    return buildGeneration(choice, metadata, mergedToolCalls);
                }).toList();

                myCopilotApi.Usage usage = chmyompletion.usage();
                Usage currentUsage = (usage != null) ? getDefaultUsage(usage) : new EmptyUsage();
                Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);

                return new ChatResponse(generations, from(chmyompletion, cumulativeUsage));
            }
            catch (Exception e) {
                logger.error("Error processing chat completion chunk", e);
                return new ChatResponse(List.of());
            }
        });
    }

    private Generation buildGeneration(myCopilotApi.Chmyompletion.Choice choice, Map<String, Object> metadata) {
        return buildGeneration(choice, metadata, choice.message().toolCalls());
    }

    private Generation buildGeneration(
            myCopilotApi.Chmyompletion.Choice choice,
            Map<String, Object> metadata,
            List<myCopilotApi.ChmyompletionMessage.ToolCall> mergedToolCalls
    ) {
        List<AssistantMessage.ToolCall> toolCalls = mergedToolCalls == null ? List.of()
                : mergedToolCalls.stream()
                    .map(toolCall -> new AssistantMessage.ToolCall(
                            toolCall.id(),
                            "function",
                            toolCall.function() == null ? null : toolCall.function().name(),
                            toolCall.function() == null ? null : toolCall.function().arguments()))
                    .toList();

        String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
        var generationMetadataBuilder = ChatGenerationMetadata.builder().finishReason(finishReason);

        String textContent = choice.message().content();
        String reasoningContent = choice.message().reasoningContent();

        myAssistantMessage assistantMessage = new myAssistantMessage(textContent, reasoningContent,
                metadata, toolCalls);
        return new Generation(assistantMessage, generationMetadataBuilder.build());
    }

    private boolean shouldExecuteTools(Prompt prompt, ChatResponse response) {
        if (!this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
            return false;
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return false;
        }

        AssistantMessage output = response.getResult().getOutput();
        if (CollectionUtils.isEmpty(output.getToolCalls())) {
            return false;
        }

        String finishReason = response.getResult().getMetadata() == null
                ? null
                : response.getResult().getMetadata().getFinishReason();

        if (!"tool_calls".equalsIgnoreCase(finishReason) && !"stop".equalsIgnoreCase(finishReason)) {
            return false;
        }

        return output.getToolCalls().stream().allMatch(this::isExecutableToolCall);
    }

    private boolean isExecutableToolCall(AssistantMessage.ToolCall toolCall) {
        if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
            return false;
        }
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return false;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall.arguments()).isObject();
        }
        catch (Exception ex) {
            return false;
        }
    }

    private ChatResponseMetadata from(myCopilotApi.Chmyompletion result, Usage usage) {
        Assert.notNull(result, "my ChmyompletionResult must not be null");
        var builder = ChatResponseMetadata.builder()
            .id(result.id() != null ? result.id() : "")
            .usage(usage)
            .model(result.model() != null ? result.model() : "")
            .keyValue("created", result.created() != null ? result.created() : 0L)
            .keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "");
        return builder.build();
    }

    private myCopilotApi.Chmyompletion chunkToChmyompletion(myCopilotApi.ChmyompletionChunk chunk) {
        List<myCopilotApi.Chmyompletion.Choice> choices = chunk.choices()
            .stream()
            .map(chunkChoice -> new myCopilotApi.Chmyompletion.Choice(
                    chunkChoice.finishReason(),
                    chunkChoice.index(),
                    chunkChoice.delta(),
                    chunkChoice.logprobs()))
            .toList();

        return new myCopilotApi.Chmyompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.serviceTier(),
                chunk.systemFingerprint(), chunk.usage());
    }

    private DefaultUsage getDefaultUsage(myCopilotApi.Usage usage) {
        return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
    }

    Prompt buildRequestPrompt(Prompt prompt) {
        myChatOptions runtimeOptions = null;
        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
                        myChatOptions.class);
            }
            else {
                runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
                        myChatOptions.class);
            }
        }

        myChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
                myChatOptions.class);

        if (runtimeOptions != null) {
            requestOptions.setInternalToolExecutionEnabled(
                    ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
                            this.defaultOptions.getInternalToolExecutionEnabled()));
            requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
                    this.defaultOptions.getToolNames()));
            requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
                    this.defaultOptions.getToolCallbacks()));
            requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
                    this.defaultOptions.getToolContext()));
        }
        else {
            requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
            requestOptions.setToolNames(this.defaultOptions.getToolNames());
            requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
            requestOptions.setToolContext(this.defaultOptions.getToolContext());
        }

        ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

        return new Prompt(prompt.getInstructions(), requestOptions);
    }

    myCopilotApi.ChmyompletionRequest createRequest(Prompt prompt, boolean stream) {
        List<myCopilotApi.ChmyompletionMessage> chmyompletionMessages = prompt.getInstructions().stream().map(message -> {
            if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
                return List.of(new myCopilotApi.ChmyompletionMessage(message.getText(),
                        myCopilotApi.ChmyompletionMessage.Role.valueOf(message.getMessageType().name())));
            }
            else if (message.getMessageType() == MessageType.ASSISTANT) {
                AssistantMessage assistantMessage = null;
                if (message instanceof AssistantMessage) {
                    assistantMessage = (AssistantMessage) message;
                }
                List<myCopilotApi.ChmyompletionMessage.ToolCall> toolCalls = null;
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
                        var function = new myCopilotApi.ChmyompletionMessage.ChmyompletionFunction(
                                toolCall.name(), toolCall.arguments());
                        return new myCopilotApi.ChmyompletionMessage.ToolCall(toolCall.id(), toolCall.type(), function);
                    }).toList();
                }
                Boolean isPrefixAssistantMessage = false;
                if (message instanceof myAssistantMessage && Boolean.TRUE.equals(((myAssistantMessage) message).getPrefix())) {
                    isPrefixAssistantMessage = true;
                }
                return List.of(new myCopilotApi.ChmyompletionMessage(assistantMessage.getText(),
                        myCopilotApi.ChmyompletionMessage.Role.ASSISTANT, null, null, toolCalls, isPrefixAssistantMessage, null));
            }
            else if (message.getMessageType() == MessageType.TOOL) {
                ToolResponseMessage toolMessage = null;
                if (message instanceof ToolResponseMessage) {
                    toolMessage = (ToolResponseMessage) message;
                }

                toolMessage.getResponses()
                    .forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));
                return toolMessage.getResponses()
                    .stream()
                    .map(tr -> new myCopilotApi.ChmyompletionMessage(tr.responseData(), myCopilotApi.ChmyompletionMessage.Role.TOOL, tr.name(),
                            tr.id(), null))
                    .toList();
            }
            throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
        }).flatMap(List::stream).toList();

        return buildRequest(chmyompletionMessages, prompt, stream);
    }

    private myCopilotApi.ChmyompletionRequest buildRequest(
            List<myCopilotApi.ChmyompletionMessage> chmyompletionMessages,
            Prompt prompt,
            boolean stream
    ) {
        myCopilotApi.ChmyompletionRequest request = new myCopilotApi.ChmyompletionRequest(chmyompletionMessages, stream);
        ChatOptions options = prompt.getOptions();
        myChatOptions requestOptions = null;
        if (options instanceof myChatOptions) {
            requestOptions = (myChatOptions) options;
        }
        request = ModelOptionsUtils.merge(requestOptions, request, myCopilotApi.ChmyompletionRequest.class);

        List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
        if (!CollectionUtils.isEmpty(toolDefinitions)) {
            request = ModelOptionsUtils.merge(
                    myChatOptions.builder().tools(this.getFunctionTools(toolDefinitions)).build(), request,
                    myCopilotApi.ChmyompletionRequest.class);
        }
        return request;
    }

    private List<myCopilotApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream().map(toolDefinition -> {
            var function = new myCopilotApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
                    toolDefinition.inputSchema());
            return new myCopilotApi.FunctionTool(function);
        }).toList();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return myChatOptions.fromOptions(this.defaultOptions);
    }

    @Override
    public String toString() {
        return "myChatModel [defaultOptions=" + this.defaultOptions + "]";
    }

    public void setObservationConvention(ChatModelObservationConvention observationConvention) {
        Assert.notNull(observationConvention, "observationConvention cannot be null");
        this.observationConvention = observationConvention;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private myCopilotApi myCopilotApi;

        private myChatOptions defaultOptions = myChatOptions.builder()
            .model(myCopilotApi.DEFAULT_CHAT_MODEL)
            .temperature(1d)
            .build();

        private ToolCallingManager toolCallingManager;

        private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

        private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private Builder() {
        }

        public Builder myCopilotApi(myCopilotApi myCopilotApi) {
            this.myCopilotApi = myCopilotApi;
            return this;
        }

        public Builder defaultOptions(myChatOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
            this.toolCallingManager = toolCallingManager;
            return this;
        }

        public Builder toolExecutionEligibilityPredicate(
                ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
            this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
            return this;
        }

        public Builder retryTemplate(RetryTemplate retryTemplate) {
            this.retryTemplate = retryTemplate;
            return this;
        }

        public Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public myChatModel build() {

            if (this.toolCallingManager != null) {
                return new myChatModel(this.myCopilotApi, this.defaultOptions, this.toolCallingManager,
                        this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
            }
            return new myChatModel(this.myCopilotApi, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
                    this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
        }

    }

    private static final class StreamingToolCallAccumulator {

        private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<myCopilotApi.ChmyompletionMessage.ToolCall>>
                toolCallsByStreamId = new java.util.concurrent.ConcurrentHashMap<>();

        List<myCopilotApi.ChmyompletionMessage.ToolCall> merge(
                String streamId,
                Integer choiceIndex,
                List<myCopilotApi.ChmyompletionMessage.ToolCall> deltaToolCalls
        ) {
            if (deltaToolCalls == null) {
                return existing(streamId);
            }

            List<myCopilotApi.ChmyompletionMessage.ToolCall> accumulated =
                    new java.util.ArrayList<>(toolCallsByStreamId.getOrDefault(key(streamId, choiceIndex), java.util.List.of()));

            for (int i = 0; i < deltaToolCalls.size(); i++) {
                myCopilotApi.ChmyompletionMessage.ToolCall current = deltaToolCalls.get(i);
                myCopilotApi.ChmyompletionMessage.ToolCall previous = i < accumulated.size() ? accumulated.get(i) : null;
                myCopilotApi.ChmyompletionMessage.ToolCall merged = mergeToolCall(previous, current);

                if (i < accumulated.size()) {
                    accumulated.set(i, merged);
                }
                else {
                    accumulated.add(merged);
                }
            }

            List<myCopilotApi.ChmyompletionMessage.ToolCall> snapshot = java.util.List.copyOf(accumulated);
            toolCallsByStreamId.put(key(streamId, choiceIndex), snapshot);
            return snapshot;
        }

        private List<myCopilotApi.ChmyompletionMessage.ToolCall> existing(String streamId) {
            return toolCallsByStreamId.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(streamId + "#"))
                    .findFirst()
                    .map(java.util.Map.Entry::getValue)
                    .orElse(java.util.List.of());
        }

        private String key(String streamId, Integer choiceIndex) {
            return streamId + "#" + (choiceIndex == null ? 0 : choiceIndex);
        }

        private myCopilotApi.ChmyompletionMessage.ToolCall mergeToolCall(
                myCopilotApi.ChmyompletionMessage.ToolCall previous,
                myCopilotApi.ChmyompletionMessage.ToolCall current
        ) {
            if (previous == null) {
                return current;
            }

            myCopilotApi.ChmyompletionMessage.ChmyompletionFunction previousFunction = previous.function();
            myCopilotApi.ChmyompletionMessage.ChmyompletionFunction currentFunction = current.function();

            String mergedName = hasText(functionName(currentFunction))
                    ? functionName(currentFunction)
                    : functionName(previousFunction);
            String mergedArguments = safe(functionArguments(previousFunction)) + safe(functionArguments(currentFunction));

            myCopilotApi.ChmyompletionMessage.ChmyompletionFunction mergedFunction =
                    new myCopilotApi.ChmyompletionMessage.ChmyompletionFunction(mergedName, mergedArguments);

            return new myCopilotApi.ChmyompletionMessage.ToolCall(
                    hasText(current.id()) ? current.id() : previous.id(),
                    hasText(current.type()) ? current.type() : previous.type(),
                    mergedFunction
            );
        }

        private String functionName(myCopilotApi.ChmyompletionMessage.ChmyompletionFunction function) {
            return function == null ? null : function.name();
        }

        private String functionArguments(myCopilotApi.ChmyompletionMessage.ChmyompletionFunction function) {
            return function == null ? null : function.arguments();
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
