package io.hollan.functions;

import com.microsoft.azure.cognitiveservices.vision.computervision.models.ImageAnalysis;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ImageTag;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.VisualFeatureTypes;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import org.json.*;

import com.microsoft.azure.cognitiveservices.vision.computervision.implementation.ComputerVisionClientImpl;

import java.util.ArrayList;
import java.util.List;

public class Function {

    public static final String apiKey = System.getenv("CognitiveServicesApiKey");
    public static final String endpoint = System.getenv("CognitiveServicesEndpoint");

    @FunctionName("analyze")
    public static void analyze(
            @BlobTrigger(path = "images", name = "image", connection = "AzureWebJobsStorage", dataType = "binary") byte[] image,
            @CosmosDBOutput(name = "outputDocument", databaseName = "images", collectionName = "analysis", connectionStringSetting = "CosmosDbConnection") OutputBinding<String> outputDocument,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Create a client.
        ComputerVisionClientImpl client = new ComputerVisionClientImpl(
            endpoint,
            builder -> builder.addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                    .build())
            )
        );
        client.withEndpoint(endpoint);
        List<VisualFeatureTypes> visualFeatureTypes = new ArrayList<>();
        visualFeatureTypes.add(VisualFeatureTypes.DESCRIPTION);
        visualFeatureTypes.add(VisualFeatureTypes.CATEGORIES);
        visualFeatureTypes.add(VisualFeatureTypes.COLOR);
        visualFeatureTypes.add(VisualFeatureTypes.FACES);
        visualFeatureTypes.add(VisualFeatureTypes.IMAGE_TYPE);
        visualFeatureTypes.add(VisualFeatureTypes.TAGS);

        ImageAnalysis result = client.computerVision().analyzeImageInStream()
                .withImage(image)
                .withVisualFeatures(visualFeatureTypes)
                .execute();

        JSONObject document = new JSONObject();
        document.put("id", java.util.UUID.randomUUID());

        if(result.description() != null && result.description().captions() != null) {
            context.getLogger().info("The image can be described as: %s\n" + result.description().captions().get(0).text());
            document.put("description", result.description().captions().get(0).text());
        }


        for(ImageTag tag : result.tags()) {
            context.getLogger().info(String.format("%s\t\t%s", tag.name(), tag.confidence()));
            document.append("tags", new JSONObject(String.format("{\"name\": \"%s\", \"confidence\": \"%s\" }", tag.name(), tag.confidence())));
        }

        context.getLogger().info(
                String.format("\nThe primary colors of this image are: %s", String.join(", ", result.color().dominantColors())));

        document.put("primaryColors", String.join(", ", result.color().dominantColors()));

        context.getLogger().info("Document: \n" + document.toString());

        outputDocument.setValue(document.toString());
    }
}
