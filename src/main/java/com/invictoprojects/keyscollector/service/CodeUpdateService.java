package com.invictoprojects.keyscollector.service;

import com.invictoprojects.keyscollector.model.CodeUpdate;
import com.invictoprojects.keyscollector.model.CodeUpdates;
import com.invictoprojects.keyscollector.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class CodeUpdateService {

    private final LanguageService languageService;
    private final StatisticsService statisticsService;

    @Autowired
    public CodeUpdateService(LanguageService languageService, StatisticsService statisticsService) {
        this.languageService = languageService;
        this.statisticsService = statisticsService;
    }

    public Flux<Message> streamCodeUpdates(CodeUpdateGenerator generator, Pattern pattern) {
        return getCodeUpdateFlux(generator)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(codeUpdate -> parseCodeUpdates(codeUpdate, pattern))
                .doOnNext(tuple -> collectLanguageStats(tuple.getT2()))
                .map(tuple -> new Message(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        statisticsService.getTopLanguageStats(),
                        isNewProject(tuple.getT3())
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    Flux<CodeUpdate> getCodeUpdateFlux(CodeUpdateGenerator generator) {
        return Flux.generate((SynchronousSink<Mono<CodeUpdates>> sink) -> sink.next(generator.getNextPage()))
                .flatMap(Flux::from, 1, 1)
                .filter(codeUpdates -> codeUpdates.getItems() != null)
                .flatMap(codeUpdates -> Flux.fromStream(codeUpdates.getItems().stream()));
    }

    Flux<Tuple3<String, String, String>> parseCodeUpdates(CodeUpdate codeUpdate, Pattern pattern) {
        return Flux.fromStream(codeUpdate.getTextMatches().stream())
                .map(pattern::matcher)
                .filter(Matcher::find)
                .map(Matcher::group)
                .map(key -> Tuples.of(key, codeUpdate.getName(), codeUpdate.getRepositoryName()));
    }

    private void collectLanguageStats(String filename) {
        String[] arr = filename.split("\\.");
        String extension = arr.length == 1 ? "Undetermined" : "." + arr[arr.length - 1];
        String language = languageService.resolveLanguageByExtension(extension);
        statisticsService.saveProgrammingLanguage(language);
    }

    private Boolean isNewProject(String projectName) {
        Boolean result = !statisticsService.isProjectAlreadySaved(projectName);
        statisticsService.saveProject(projectName);
        return result;
    }
}
