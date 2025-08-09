import OpenAI from 'openai';

const openai = new OpenAI({
  apiKey: process.env.REACT_APP_OPENAI_API_KEY,
  dangerouslyAllowBrowser: true // Note: This is not recommended for production
});

export async function getGPTResponse(prompt: string): Promise<string> {
  try {
    const completion = await openai.chat.completions.create({
      messages: [{ role: "user", content: prompt }],
      model: "gpt-3.5-turbo",
    });

    return completion.choices[0]?.message?.content || 'No response from GPT';
  } catch (error) {
    console.error('Error calling OpenAI API:', error);
    return 'Error: Failed to get response from GPT';
  }
} 