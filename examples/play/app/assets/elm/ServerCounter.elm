module ServerCounter exposing (..)

import Html exposing (..)
import Html.Events exposing (onClick)
import Http
import Json.Decode as Json


main : Program Never Model Msg
main =
    program
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }



-- MODEL


type alias Model =
    { counter : Int
    , error : Maybe String
    }


init : ( Model, Cmd Msg )
init =
    ( Model 0 Nothing, incrementCounterServer )



-- UPDATE


type Msg
    = IncrementServerCounter
    | ServerCounterUpdated (Result Http.Error Int)


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        IncrementServerCounter ->
            ( model, incrementCounterServer )

        ServerCounterUpdated (Ok newCounter) ->
            ( { model | counter = newCounter, error = Nothing }, Cmd.none )

        ServerCounterUpdated (Err newError) ->
            ( { model | error = Just <| toString newError }, Cmd.none )



-- VIEW


view : Model -> Html Msg
view model =
    div []
        [ button [ onClick IncrementServerCounter ] [ text "Increment Server" ]
        , div [] [ text (toString model.counter) ]
        , div [] [ text (Maybe.withDefault "" model.error) ]
        ]



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none



-- HTTP


incrementCounterServer : Cmd Msg
incrementCounterServer =
    Http.send ServerCounterUpdated (Http.get "/count" decodeCounter)


decodeCounter : Json.Decoder Int
decodeCounter =
    Json.at [ "counter" ] Json.int
