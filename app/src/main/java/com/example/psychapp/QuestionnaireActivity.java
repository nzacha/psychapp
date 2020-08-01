package com.example.psychapp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.psychapp.Question.QuestionType;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;

public class QuestionnaireActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questionnaire);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());

        final ArrayList<Question> questions = new ArrayList<Question>();
        QuizAdapter adapter = new QuizAdapter(this, questions);
        ListView quizQuestionList = findViewById(R.id.quiz_question_list);
        quizQuestionList.setAdapter(adapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Button sendAnswersButton = (Button) findViewById(R.id.send_answers_button);
        sendAnswersButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                for(Question question: questions) {
                    sendAnswerToServer(question, PsychApp.userId);
                    finish();
                }
            }
        });

        retrieveQuestionsFromServer(questions, PsychApp.researcher);
    }

    private void sendAnswerToServer(Question question, int userId){
        // Instantiate the RequestQueue.
        final RequestQueue queue = Volley.newRequestQueue(this);
        String url = PsychApp.serverUrl + "answers/" + question.id + "/" + userId;

        Map<String, String> params = new HashMap<String, String>();
        String answer = null;
        switch(question.type) {
            case TEXT:
                EditText questionText = findViewById(new String("question"+question.id).hashCode());
                answer = questionText.getText().toString();
                break;
            case SLIDER:
            case SLIDER_DISCRETE:
                SeekBar seekbar = findViewById(new String("question"+question.id).hashCode());
                answer = ""+seekbar.getProgress();
                break;
            case MULTIPLE_CHOICE:
                RadioGroup radioGroup = findViewById(new String("question"+question.id).hashCode());
                int selectedId = radioGroup.getCheckedRadioButtonId();
                RadioButton radioButton = (RadioButton) findViewById(selectedId);
                answer = ""+radioButton.getId();
                break;
            default:
                throw new InputMismatchException();
        }
        params.put("text", answer);

        JsonObjectRequest postRequest = new JsonObjectRequest(Request.Method.POST, url, new JSONObject(params),
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d("wtf", response.toString());
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //Log.d("wtf", "Error: " + error.getMessage());
                }
            });

        // add it to the RequestQueue
        queue.add(postRequest);
    }

    private void retrieveQuestionsFromServer(final ArrayList<Question> questions, int researcherId){
        // Instantiate the RequestQueue.
        final RequestQueue queue = Volley.newRequestQueue(this);
        String url = PsychApp.serverUrl + "questions/" + researcherId;

        // prepare the Request
        JsonArrayRequest getRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONArray>(){
                    @Override
                    public void onResponse(JSONArray response) {
                        for( int i=0; i< response.length(); i++){
                            int id = -1;
                            String  question = null;
                            String type = null;
                            JSONArray options = null;
                            int level = 1;
                            try {
                                JSONObject questionObj = response.getJSONObject(i);
                                id = Integer.parseInt(questionObj.get("id").toString());
                                question = questionObj.get("question_text").toString();
                                type = questionObj.get("question_type").toString().toUpperCase();
                                if(questionObj.has("question_options"))
                                    options = questionObj.getJSONArray("question_options");
                                if(questionObj.has("levels"))
                                    level = Integer.parseInt(questionObj.get("levels").toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            //multiple choice
                            if(options != null && options.length() > 1) {
                                String[] optionsList = new String[options.length()];
                                for(int j = 0; j < options.length(); j++){
                                    try {
                                        optionsList[j] = options.getJSONObject(j).get("option").toString();
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                questions.add(new Question(id, question, optionsList));
                            }//sliders
                            else if (type.equals(QuestionType.SLIDER.name()) || type.equals(QuestionType.SLIDER_DISCRETE.name())){
                                if(level == 1) {
                                    questions.add(new Question(id, question, QuestionType.SLIDER, level));
                                }else{
                                    questions.add(new Question(id, question, QuestionType.SLIDER_DISCRETE, level));
                                }
                            }//text
                            else{
                                questions.add(new Question(id, question, QuestionType.TEXT));
                            }
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("help", error.getLocalizedMessage());
                    }
                }
        );

        // add it to the RequestQueue
        queue.add(getRequest);
    }

    class QuizAdapter extends ArrayAdapter<Question>{
        public QuizAdapter(@NonNull Context context, ArrayList<Question> questions) {
            super(context, 0, questions);
        }

        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            Question question = getItem(position);

            if (convertView == null) {
                switch(question.type) {
                    case TEXT:
                        convertView = LayoutInflater.from(getContext()).inflate(R.layout.quiz_question, parent, false);
                        TextView message = convertView.findViewById(R.id.quiz_question_message);
                        message.setId(new String("question"+question.id).hashCode());
                        message.setText(Question.Placeholder);
                        break;
                    case SLIDER:
                        convertView = LayoutInflater.from(getContext()).inflate(R.layout.quiz_question_slider, parent, false);
                        SeekBar continuousSeekBar = convertView.findViewById(R.id.question_seekbar);
                        continuousSeekBar.setId(new String("question"+question.id).hashCode());
                        continuousSeekBar.setMax(100);
                        continuousSeekBar.setProgress(0);
                        break;
                    case SLIDER_DISCRETE:
                        convertView = LayoutInflater.from(getContext()).inflate(R.layout.quiz_question_slider_discrete, parent, false);
                        SeekBar discreteSeekBar = convertView.findViewById(R.id.question_seekbar);
                        discreteSeekBar.setId(new String("question"+question.id).hashCode());
                        discreteSeekBar.setMax(question.level-1);
                        discreteSeekBar.setProgress(0);
                        break;
                    case MULTIPLE_CHOICE:
                        convertView = LayoutInflater.from(getContext()).inflate(R.layout.quiz_question_multiple_choice, parent, false);
                        RadioGroup optionsGroup = convertView.findViewById(R.id.choice_group);
                        optionsGroup.setId(new String("question"+question.id).hashCode());
                        for(int i=0; i < question.options.length; i++){
                            RadioButton newButton = new RadioButton( getContext());
                            newButton.setText(question.options[i]);
                            newButton.setId(i);
                            optionsGroup.addView(newButton);
                        }
                        optionsGroup.check(0);
                        break;
                    default:
                        throw new InputMismatchException();
                }
            }
            TextView title = convertView.findViewById(R.id.quiz_question_title);
            title.setText(question.question);
            return convertView;
        }
    }
}