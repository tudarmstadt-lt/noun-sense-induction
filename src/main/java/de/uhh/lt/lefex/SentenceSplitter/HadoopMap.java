package de.uhh.lt.lefex.SentenceSplitter;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;
import java.io.IOException;
import java.util.Collection;
import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;
import org.jsoup.Jsoup;


class HadoopMap extends Mapper<LongWritable, Text, LongWritable, Text> {
    Logger log = Logger.getLogger("de.uhh.lt.lefex");
	AnalysisEngine segmenter;
	JCas jCas;
    int maxSentenceSizeTokens;
    boolean stripHtml;

	@Override
	public void setup(Context context) {
        maxSentenceSizeTokens = context.getConfiguration().getInt("max_sentence_size", 110);
        stripHtml = context.getConfiguration().getBoolean("strip_html", false);
        log.info("Max sentence size (tokens): " + maxSentenceSizeTokens);
        log.info("Strip HTML tags: " + stripHtml);

        try {
            segmenter = AnalysisEngineFactory.createEngine(OpenNlpSegmenter.class);
            jCas = CasCreationUtils.createCas(createTypeSystemDescription(), null, null).getJCas();
        } catch (ResourceInitializationException e) {
            log.error("Couldn't initialize analysis engine", e);
        } catch (CASException e) {
            log.error("Couldn't create new CAS", e);
        }
	}

	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		try {
            String text = value.toString();
            if (stripHtml) text = Jsoup.parse(text).text();

            context.getCounter("de.tudarmstadt.lt", "TOTAL_LINES").increment(1);
            jCas.reset();
            jCas.setDocumentText(text);
            jCas.setDocumentLanguage("en");
            segmenter.process(jCas);

            for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
                Collection<Token> tokens = JCasUtil.selectCovered(jCas, Token.class, sentence);
                context.getCounter("de.tudarmstadt.lt", "SENTENCES_TOTAL").increment(1);
                if(tokens.size() <= maxSentenceSizeTokens) {
                    String sentenceStr = text
                            .substring(sentence.getBegin(), sentence.getEnd())
                            .replaceAll("\\s+", " ");
                    context.write(new LongWritable(sentenceStr.hashCode()), new Text(sentenceStr));
                    context.getCounter("de.tudarmstadt.lt", "SENTENCES_WRITTEN").increment(1);
                } else {
                    context.getCounter("de.tudarmstadt.lt", "SENTENCES_SKIPPED").increment(1);
                }
            }

        } catch(Exception e){
            log.error("Can't process line: " + value.toString(), e);
            context.getCounter("de.tudarmstadt.lt.wiki", "NUM_MAP_ERRORS").increment(1);
        }
    }
}
