module ServerCounter exposing (..)

import Html exposing (..)
import Html.App as App
import Html.Events exposing (onClick)
import Http
import Json.Decode as Json
import Task


main : Program Never
main =
    App.program
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
    | ServerCounterUpdated Int
    | ServerCounterUpdateFailed Http.Error


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        IncrementServerCounter ->
            ( model, incrementCounterServer )

        ServerCounterUpdated newCounter ->
            ( { model | counter = newCounter, error = Nothing }, Cmd.none )

        ServerCounterUpdateFailed newError ->
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
    Task.perform ServerCounterUpdateFailed ServerCounterUpdated (Http.get decodeCounter "/count")


decodeCounter : Json.Decoder Int
decodeCounter =
    Json.at [ "counter" ] Json.int
