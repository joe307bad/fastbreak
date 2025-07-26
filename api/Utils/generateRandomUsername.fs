module api.Utils.generateRandomUsername

open System

let private words = [
    "fast"; "cool"; "slam"; "boom"; "jump"; "race"; "fire"; 
    "bolt"; "rush"; "dash"; "zoom"; "spin"; "flip"; "kick"; 
    "star"; "rock"; "wave"; "glow"; "moon"; "nova"; "wind"; 
    "hawk"; "wolf"; "bear"; "lion"; "fox"; "deer"; "owl";
    "blue"; "red"; "gold"; "pink"; "gray"; "lime"; "teal"
]

let generateRandomUsername () =
    let random = Random()
    let word1 = words.[random.Next(words.Length)]
    let word2 = words.[random.Next(words.Length)]
    let word3 = words.[random.Next(words.Length)]
    let number = random.Next(1000, 9999)
    $"{word1}-{word2}-{word3}-{number}"
