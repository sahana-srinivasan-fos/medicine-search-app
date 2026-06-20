from pyngrok import ngrok

public_url = ngrok.connect(8000)
print(public_url)

